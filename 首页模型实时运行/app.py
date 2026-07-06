# -*- coding: utf-8 -*-
"""
首页水网模型 — 单步运行模式
由 Spring Boot RealtimeModelScheduler 每分钟调用一次：
  python app.py --step --state-file solver_state.pkl
每次调用：
  1. 从 hydrosim API 获取实时边界值
  2. 若 state-file 存在则恢复 solver 状态，否则重新初始化
  3. 推进一个时步 (dt=60s)
  4. 将结果写入 waternet.db
  5. 将 solver 状态序列化到 state-file 供下次使用
  6. 向 stdout 输出单行 JSON 摘要供 Java 读取
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import pickle
import sqlite3
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import Dict, List

import numpy as np
import requests

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent
DATABASE_PATH = PROJECT_ROOT / "waternet" / "data" / "waternet.db"

LATEST_URL    = "https://waterlevel.gd.hydrosim.cn/api/scenario/latest"
MERGED_URL    = "https://waterlevel.gd.hydrosim.cn/api/scenario/{channel}/monitoring"
TIMEOUT = 10
DT = 60.0
WARMUP_STEPS = 30
MAX_SERIES_ROWS = 200
CHANNEL_NODE_MAP = {
    "taihu":       {"node": 1, "bc_type": "stage"},
    "canal-south": {"node": 3, "bc_type": "stage"},
    "canal-north": {"node": 6, "bc_type": "flow", "negate": True},
}


def fetch_boundary_node_values() -> Dict[int, float]:
    try:
        resp = requests.get(LATEST_URL, timeout=TIMEOUT)
        resp.raise_for_status()
        channels = resp.json().get("channels", {})
    except Exception as e:
        print(f"[WARN] API失败: {e}，使用回退值", file=sys.stderr)
        return {1: 13.5, 3: 13.5, 6: -120.0}

    result: Dict[int, float] = {}
    for ch_key, cfg in CHANNEL_NODE_MAP.items():
        value = float(channels.get(ch_key, {}).get("value", 0.0))
        if cfg.get("negate"):
            value = -value
        result[cfg["node"]] = value
    return result



def fetch_history_records(n: int = WARMUP_STEPS) -> List[Dict]:
    """从外部 API 拉取最近 n 条监测记录，返回按时间正序排列的列表。
    每条格式: {"ts": int(unix秒), "boundary": {1: z, 3: z, 6: q}}
    """
    channel_cfg = {
        "taihu":       {"node": 1, "negate": False},
        "canal-south": {"node": 3, "negate": False},
        "canal-north": {"node": 6, "negate": True},
    }
    raw: Dict[str, List[tuple]] = {}
    for ch_key in channel_cfg:
        try:
            url = MERGED_URL.format(channel=ch_key)
            resp = requests.get(url, params={"page_size": n * 3}, timeout=TIMEOUT)
            resp.raise_for_status()
            body = resp.json()
            records = body if isinstance(body, list) else body.get("data", body.get("records", []))
            pts: List[tuple] = []
            for rec in records:
                ts_str = rec.get("timestamp") or rec.get("time") or rec.get("t")
                val    = rec.get("value") or rec.get("v")
                if ts_str is None or val is None:
                    continue
                try:
                    dt = datetime.fromisoformat(str(ts_str).replace("Z", "+00:00"))
                    pts.append((int(dt.timestamp()), float(val)))
                except Exception:
                    continue
            pts.sort(key=lambda x: x[0])
            raw[ch_key] = pts
        except Exception as e:
            print(f"[WARN] 拉取历史 {ch_key} 失败: {e}", file=sys.stderr)
            raw[ch_key] = []

    # 三通道共有的时刻（精确匹配）
    if all(raw.get(ch) for ch in channel_cfg):
        ts_sets = [set(ts for ts, _ in raw[ch]) for ch in channel_cfg]
        common = sorted(ts_sets[0] & ts_sets[1] & ts_sets[2])
    else:
        common = []

    if not common:
        # 回退：按索引对齐取最短长度
        min_len = min((len(v) for v in raw.values() if v), default=0)
        if min_len == 0:
            return []
        aligned = {ch: raw[ch][-min_len:] for ch in channel_cfg}
        result_list = []
        for i in range(min_len):
            bc: Dict[int, float] = {}
            for ch_key, cfg in channel_cfg.items():
                val = aligned[ch_key][i][1]
                bc[cfg["node"]] = -val if cfg["negate"] else val
            result_list.append({"ts": aligned["taihu"][i][0], "boundary": bc})
        return result_list[-n:]

    val_map = {ch: {ts: v for ts, v in raw[ch]} for ch in channel_cfg}
    result_list = []
    for ts in common:
        bc: Dict[int, float] = {}
        for ch_key, cfg in channel_cfg.items():
            val = val_map[ch_key].get(ts, 0.0)
            bc[cfg["node"]] = -val if cfg["negate"] else val
        result_list.append({"ts": ts, "boundary": bc})
    return result_list[-n:]


def _clear_timeseries_db():
    """清空时序数据，重新启动后画图不会出现断跳。"""
    with sqlite3.connect(DATABASE_PATH) as conn:
        conn.execute("DELETE FROM node_hydrology_timeseries")
        conn.execute("DELETE FROM node_latest_hydrology")
        conn.execute("DELETE FROM segment_profile_results")
        conn.execute("DELETE FROM segment_hydrology_stats")


def node_code(node_id: int) -> str:
    return f"N{node_id:02d}"


def load_preissmann_module():
    module_path = SCRIPT_DIR / "HHU_rivernetwork_preissmann_code..py"
    spec = importlib.util.spec_from_file_location("preissmann", str(module_path))
    mod = importlib.util.module_from_spec(spec)
    sys.modules["preissmann"] = mod
    spec.loader.exec_module(mod)
    return mod


def build_boundary_conditions(mod, live: Dict[int, float]) -> dict:
    bc = {
        1: mod.BoundaryCondition("stage", live[1]),
        3: mod.BoundaryCondition("stage", live[3]),
        6: mod.BoundaryCondition("flow",  live[6]),
    }
    for dead_end in range(7, 21):
        bc[dead_end] = mod.BoundaryCondition("flow", 0.0)
    return bc


def init_solver(mod, live: Dict[int, float]):
    reach_table = mod.load_reach_table_from_csv(SCRIPT_DIR / "拓扑河段表.csv")
    node_matrix  = mod.load_node_matrix_from_csv(SCRIPT_DIR / "node_matrix.csv")

    bc_input = {
        1:  {"type": "stage", "value": live[1]},
        3:  {"type": "stage", "value": live[3]},
        6:  {"type": "flow",  "value": live[6]},
    }
    for dead_end in range(7, 21):
        bc_input[dead_end] = {"type": "flow", "value": 0.0}

    solver = mod.RiverNetworkPreissmannSolver(
        reach_table=reach_table,
        node_matrix=node_matrix,
        boundary_conditions=bc_input,
        dt=DT,
        theta=0.65,
        initial_node_heads={1: live[1], 3: live[3], 6: live[3]},
        default_initial_depth=live[1],
        outer_tol=1.0e-5,
        outer_maxiter=20,
        outer_relaxation=0.8,
    )
    return solver, reach_table


def node_flow(node_id: int, reach_q: list, reach_table: list) -> float:
    total = 0.0
    for idx, rm in enumerate(reach_table):
        sn, en = int(rm["start_node"]), int(rm["end_node"])
        q_arr = reach_q[idx]
        if en == node_id:
            total += float(q_arr[-1])   # 流入节点，正
        if sn == node_id:
            total -= float(q_arr[0])    # 流出节点，负
    return total


def write_step_to_db(
    step_no: int,
    node_ids: list,
    node_heads: np.ndarray,
    reach_q: list,
    reach_z: list,
    reach_table: list,
    live: Dict[int, float],
    ts: int | None = None,
):
    now_ts = ts if ts is not None else int(datetime.now().timestamp())
    now_str = datetime.fromtimestamp(now_ts).isoformat(timespec="seconds")

    node_rows: List[tuple] = []
    for col, nid in enumerate(node_ids):
        z = round(float(node_heads[col]), 4)
        q = round(node_flow(nid, reach_q, reach_table), 4)
        node_rows.append((now_ts, node_code(nid), z, q))

    profile_rows: List[tuple] = []
    for ridx, rm in enumerate(reach_table):
        rid = int(rm.get("reach_id", ridx))
        sn, en = int(rm["start_node"]), int(rm["end_node"])
        z_arr, q_arr = reach_z[ridx], reach_q[ridx]
        n_sec = len(z_arr)
        x_prof = np.linspace(0.0, float(rm["length"]), n_sec)
        for sec in range(n_sec):
            profile_rows.append((
                f"REAL_RIVER_{rid:02d}", rid,
                node_code(sn), node_code(en), sec,
                round(float(x_prof[sec]), 2),
                round(float(z_arr[sec]), 4),
                round(float(q_arr[sec]), 4),
            ))

    with sqlite3.connect(DATABASE_PATH) as conn:
        conn.executemany(
            "INSERT OR REPLACE INTO node_hydrology_timeseries (hour, node_code, water_level, flow) "
            "VALUES (?, ?, ?, ?)", node_rows)
        # 每个节点只保留最近 MAX_SERIES_ROWS 条
        conn.execute("""
            DELETE FROM node_hydrology_timeseries
            WHERE rowid IN (
                SELECT rowid FROM (
                    SELECT rowid,
                           ROW_NUMBER() OVER (PARTITION BY node_code ORDER BY hour DESC) AS rn
                    FROM node_hydrology_timeseries
                ) WHERE rn > ?
            )
        """, (MAX_SERIES_ROWS,))

        conn.execute("DELETE FROM segment_profile_results")
        conn.executemany(
            "INSERT INTO segment_profile_results "
            "(segment_code, reach_id, start_node_code, end_node_code, "
            "section_no, x_m, water_level, flow) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            profile_rows)

        conn.execute("DELETE FROM node_latest_hydrology")
        conn.executemany(
            "INSERT OR REPLACE INTO node_latest_hydrology (hour, node_code, water_level, flow) "
            "VALUES (?, ?, ?, ?)", node_rows)

        conn.execute("DELETE FROM segment_hydrology_stats")
        conn.execute(
            "INSERT INTO segment_hydrology_stats "
            "(segment_code, max_flow, min_flow, max_water_level, min_water_level, "
            "profile_hour, sample_count) "
            "SELECT segment_code, max(flow), min(flow), max(water_level), min(water_level), "
            "?, count(*) FROM segment_profile_results GROUP BY segment_code",
            (now_ts,))

        conn.execute(
            "INSERT OR REPLACE INTO platform_observations "
            "(id, observed_at, taihu_water_level, canal_north_flow, canal_south_water_level) "
            "VALUES (1, ?, ?, ?, ?)",
            (now_str, live[1], -live[6], live[3]))



def warmup_from_history(mod, state_file: Path):
    """用历史边界数据预热模型，结果以历史时间戳写入 DB，然后保存 solver 状态。"""
    records = fetch_history_records(WARMUP_STEPS)
    if not records:
        print("[WARN] 无法获取历史数据，跳过预热", file=sys.stderr)
        return

    print(f"[INFO] 开始历史预热，共 {len(records)} 步", file=sys.stderr)
    live0 = records[0]["boundary"]
    solver, reach_table = init_solver(mod, live0)

    for step_idx, rec in enumerate(records):
        live = rec["boundary"]
        hist_ts = rec["ts"]

        bc = build_boundary_conditions(mod, live)
        for nid, bc_obj in bc.items():
            idx = solver.id_to_index.get(nid)
            if idx is not None:
                solver.boundary_conditions[idx] = bc_obj

        result = solver.solve(n_steps=1, save_full_reach_history=True)
        solver.current_reach_states = [st.copy() for st in result["final_reach_states"]]
        solver.current_node_heads   = result["node_head_history"][-1].copy()

        write_step_to_db(
            step_no=step_idx + 1,
            node_ids=result["node_ids"],
            node_heads=result["node_head_history"][-1],
            reach_q=result["reach_history_q"][-1],
            reach_z=result["reach_history_z"][-1],
            reach_table=reach_table,
            live=live,
            ts=hist_ts,
        )

    # 保存最终 solver 状态供后续 run_single_step 使用
    with open(state_file, "wb") as f:
        import pickle as _pkl
        _pkl.dump({"solver": solver, "reach_table": reach_table, "step_no": len(records)}, f)
    print(f"[INFO] 历史预热完成，solver 状态已保存", file=sys.stderr)


def run_single_step(state_file: Path):
    mod = load_preissmann_module()
    live = fetch_boundary_node_values()

    MAX_GAP_SECONDS = 1800  # 超过30分钟未运行则重新初始化

    # 恢复或初始化 solver
    step_no = 1
    if state_file.exists():
        try:
            state_age = time.time() - state_file.stat().st_mtime
            if state_age > MAX_GAP_SECONDS:
                print(f"[INFO] 距上次运行 {state_age/3600:.1f} 小时，重新初始化 solver 并清空旧时序数据", file=sys.stderr)
                state_file.unlink()
                _clear_timeseries_db()
                warmup_from_history(mod, state_file)
                if state_file.exists():
                    with open(state_file, "rb") as _wf:
                        import pickle as _pkl3
                        _ws = _pkl3.load(_wf)
                    solver = _ws["solver"]
                    reach_table = _ws["reach_table"]
                    step_no = _ws.get("step_no", 0) + 1
                else:
                    solver, reach_table = init_solver(mod, live)
                raise ValueError("warmup_done")

            with open(state_file, "rb") as f:
                state = pickle.load(f)
            solver = state["solver"]
            reach_table = state["reach_table"]
            step_no = state.get("step_no", 0) + 1
            # 更新边界条件为最新实时值
            bc = build_boundary_conditions(mod, live)
            for nid, bc_obj in bc.items():
                idx = solver.id_to_index.get(nid)
                if idx is not None:
                    solver.boundary_conditions[idx] = bc_obj
            print(f"[INFO] 恢复 solver 状态, step={step_no}", file=sys.stderr)
        except Exception as e:
            if "warmup_done" not in str(e):
                print(f"[WARN] 恢复状态失败 ({e})，重新初始化", file=sys.stderr)
                solver, reach_table = init_solver(mod, live)
    else:
        print("[INFO] 首次运行，历史预热并初始化 solver", file=sys.stderr)
        warmup_from_history(mod, state_file)
        # 预热完成后重新加载 state_file 中的 solver
        if state_file.exists():
            with open(state_file, "rb") as f:
                import pickle as _pkl2
                state = _pkl2.load(f)
            solver = state["solver"]
            reach_table = state["reach_table"]
        else:
            solver, reach_table = init_solver(mod, live)

    # 推进一步
    result = solver.solve(n_steps=1, save_full_reach_history=True)
    solver.current_reach_states = [st.copy() for st in result["final_reach_states"]]
    solver.current_node_heads   = result["node_head_history"][-1].copy()

    node_ids   = result["node_ids"]
    node_heads = result["node_head_history"][-1]
    reach_q    = result["reach_history_q"][-1]
    reach_z    = result["reach_history_z"][-1]

    # 写数据库
    write_step_to_db(step_no, node_ids, node_heads, reach_q, reach_z, reach_table, live)

    # 保存 solver 状态
    with open(state_file, "wb") as f:
        pickle.dump({"solver": solver, "reach_table": reach_table, "step_no": step_no}, f)

    # 输出 JSON 摘要给 Java 读取
    node_heads_dict = {str(nid): round(float(node_heads[i]), 4)
                       for i, nid in enumerate(node_ids)}
    summary = {
        "status": "ok",
        "step": step_no,
        "timestamp": datetime.now().isoformat(timespec="seconds"),
        "nodeHeads": node_heads_dict,
        "boundary": {str(k): round(v, 4) for k, v in live.items()},
    }
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--step", action="store_true", help="单步推进模式")
    parser.add_argument("--state-file", default=str(SCRIPT_DIR / "solver_state.pkl"))
    args = parser.parse_args()

    if args.step:
        run_single_step(Path(args.state_file))
    else:
        print("用法: python app.py --step [--state-file path/to/state.pkl]")
        sys.exit(1)
