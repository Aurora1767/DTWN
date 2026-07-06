# -*- coding: utf-8 -*-
"""河网 Preissmann 求解模型桥接服务 — 供 Spring Boot 调用。"""

from __future__ import annotations

import argparse
import importlib.util
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
RIVER_NETWORK_DIR = SCRIPT_DIR / "river_network"
if str(RIVER_NETWORK_DIR) not in sys.path:
    sys.path.insert(0, str(RIVER_NETWORK_DIR))
MODEL_PATH = RIVER_NETWORK_DIR / "HHU_rivernetwork_preissmann_code..py"
DEFAULT_DT = 300.0
DEFAULT_FORECAST_HOURS = 1.0
_CACHED_MODEL_MODULE = None


def resolve_step_count(forecast_hours: float, dt: float = DEFAULT_DT) -> int:
    if forecast_hours <= 0:
        raise ValueError("预报时长必须大于 0")
    return max(1, int(forecast_hours * 3600.0 / dt))


def _load_model_module():
    global _CACHED_MODEL_MODULE
    if _CACHED_MODEL_MODULE is not None:
        return _CACHED_MODEL_MODULE

    spec = importlib.util.spec_from_file_location("hhu_rivernetwork_preissmann", MODEL_PATH)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"无法加载河网模型: {MODEL_PATH}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    _CACHED_MODEL_MODULE = module
    return module


from gate_hydraulics import build_gate_nodes_config


def run_forecast(
    n_steps: int | None = None,
    dt: float = DEFAULT_DT,
    forecast_hours: float | None = None,
    boundary_values: dict[int, float] | None = None,
    boundary_series: dict[int, list[float]] | None = None,
    initial_node_heads_override: dict[int, float] | None = None,
    simulation_start_at: str | None = None,
    gate_openings: dict[int, float] | None = None,
) -> dict:
    model = _load_model_module()

    if forecast_hours is not None:
        normalized_hours = float(forecast_hours)
        n_steps = resolve_step_count(normalized_hours, dt)
    elif n_steps is None:
        normalized_hours = DEFAULT_FORECAST_HOURS
        n_steps = resolve_step_count(normalized_hours, dt)
    else:
        normalized_hours = round(max(1, n_steps) * dt / 3600.0, 4)
        n_steps = max(1, n_steps)

    reach_table = model.load_reach_table_from_csv(RIVER_NETWORK_DIR / "拓扑河段表.csv")
    node_matrix = model.load_node_matrix_from_csv(RIVER_NETWORK_DIR / "node_matrix.csv")
    live_boundary_values = boundary_values or model.fetch_constant_boundary_values()
    step_count = n_steps + 1
    series_map = boundary_series or {}

    def resolve_boundary_value(node_id: int) -> float | list[float]:
        if node_id in series_map:
            series = [float(value) for value in series_map[node_id]]
            if len(series) != step_count:
                raise ValueError(
                    f"节点 {node_id} 边界序列长度应为 {step_count}，实际为 {len(series)}"
                )
            return series
        if node_id in live_boundary_values:
            return float(live_boundary_values[node_id])
        raise KeyError(f"缺少节点 {node_id} 的边界条件")

    boundary_conditions = {
        1: {"type": "stage", "value": resolve_boundary_value(1)},
        3: {"type": "stage", "value": resolve_boundary_value(3)},
        6: {"type": "flow", "value": resolve_boundary_value(6)},
    }
    for dead_end_node in range(7, 21):
        boundary_conditions[dead_end_node] = {"type": "flow", "value": 0.0}

    if initial_node_heads_override:
        initial_node_heads = {
            int(node_id): float(value) for node_id, value in initial_node_heads_override.items()
        }
    else:
        initial_node_heads = {
            1: live_boundary_values[1],
            3: live_boundary_values[3],
            6: live_boundary_values[3],
        }

    gate_nodes_config = None
    if boundary_values is not None:
        gate_nodes_config = build_gate_nodes_config(gate_openings or {})

    solver = model.RiverNetworkPreissmannSolver(
        reach_table=reach_table,
        node_matrix=node_matrix,
        boundary_conditions=boundary_conditions,
        dt=dt,
        theta=0.65,
        initial_node_heads=initial_node_heads,
        gate_nodes=gate_nodes_config,
        default_initial_depth=13.5,
        outer_tol=1.0e-5,
        outer_maxiter=20,
        outer_relaxation=0.8,
    )

    result = solver.solve(n_steps=n_steps, save_full_reach_history=True)
    node_head_history = result["node_head_history"]
    reach_history_q = result["reach_history_q"]
    reach_history_z = result["reach_history_z"]
    final_heads = node_head_history[-1]
    node_ids = [int(node_id) for node_id in result["node_ids"]]
    node_heads = {
        str(node_id): round(float(final_heads[index]), 4)
        for index, node_id in enumerate(node_ids)
    }

    final_states = result["final_reach_states"]
    net_outflows = model.compute_node_net_outflows(solver, final_states)
    node_flows = {
        str(solver.index_to_id[index]): round(float(net_outflows[index]), 4)
        for index in range(solver.n_nodes)
    }

    nodes = []
    node_histories = []
    node_flow_history = []
    for step in range(step_count):
        step_net_flows = model.compute_node_net_outflows(solver, reach_history_q[step])
        node_flow_history.append(step_net_flows)

    for index in range(solver.n_nodes):
        node_id = int(solver.index_to_id[index])
        water_levels = [
            round(float(node_head_history[step, index]), 4) for step in range(step_count)
        ]
        net_flows = [
            round(float(node_flow_history[step][index]), 4) for step in range(step_count)
        ]
        nodes.append(
            {
                "nodeId": node_id,
                "waterLevel": water_levels[-1],
                "netFlow": net_flows[-1],
            }
        )
        node_histories.append(
            {
                "nodeId": node_id,
                "waterLevels": water_levels,
                "netFlows": net_flows,
            }
        )

    reaches = []
    reach_profiles = []
    reach_histories = []
    for reach_index, (reach_solver, state) in enumerate(zip(solver.reaches, final_states)):
        reach_data = reach_solver.data
        reach_id = int(reach_data.reach_id) + 1
        water_levels = [round(float(value), 4) for value in state.z.tolist()]
        flows = [round(float(value), 4) for value in state.q.tolist()]
        distances = [round(index * reach_solver.dx, 1) for index in range(len(water_levels))]
        inlet_flows = []
        outlet_flows = []
        inlet_water_levels = []
        outlet_water_levels = []
        for step in range(step_count):
            q_profile = reach_history_q[step][reach_index]
            z_profile = reach_history_z[step][reach_index]
            inlet_flows.append(round(float(q_profile[0]), 4))
            outlet_flows.append(round(float(q_profile[-1]), 4))
            inlet_water_levels.append(round(float(z_profile[0]), 4))
            outlet_water_levels.append(round(float(z_profile[-1]), 4))

        reaches.append(
            {
                "reachId": reach_id,
                "startNode": int(reach_data.start_node),
                "endNode": int(reach_data.end_node),
                "length": round(float(reach_data.length), 1),
                "width": round(float(reach_data.width), 2),
                "inletFlow": flows[0],
                "outletFlow": flows[-1],
                "avgWaterLevel": round(float(state.z.mean()), 4),
                "maxWaterLevel": round(float(state.z.max()), 4),
                "minWaterLevel": round(float(state.z.min()), 4),
                "avgFlow": round(float(abs(state.q).mean()), 4),
            }
        )
        reach_profiles.append(
            {
                "reachId": reach_id,
                "startNode": int(reach_data.start_node),
                "endNode": int(reach_data.end_node),
                "label": f"河段{reach_id} ({reach_data.start_node}-{reach_data.end_node})",
                "length": round(float(reach_data.length), 1),
                "distances": distances,
                "waterLevels": water_levels,
                "flows": flows,
            }
        )
        reach_histories.append(
            {
                "reachId": reach_id,
                "startNode": int(reach_data.start_node),
                "endNode": int(reach_data.end_node),
                "label": f"河段{reach_id} ({reach_data.start_node}-{reach_data.end_node})",
                "inletFlows": inlet_flows,
                "outletFlows": outlet_flows,
                "inletWaterLevels": inlet_water_levels,
                "outletWaterLevels": outlet_water_levels,
            }
        )

    return {
        "status": "success",
        "forecastHours": round(normalized_hours, 4),
        "nSteps": n_steps,
        "dt": dt,
        "simulatedSeconds": round(n_steps * dt, 1),
        "timestamp": simulation_start_at or datetime.now(timezone.utc).astimezone().isoformat(),
        "simulationStartAt": simulation_start_at,
        "nodeHeads": node_heads,
        "nodeFlows": node_flows,
        "boundaryValues": {
            str(node_id): round(float(value), 4)
            for node_id, value in live_boundary_values.items()
        },
        "nodes": nodes,
        "nodeHistories": node_histories,
        "reaches": reaches,
        "reachProfiles": reach_profiles,
        "reachHistories": reach_histories,
    }


def _load_json_map(raw: str | None, file_path: str | None) -> dict | None:
    if file_path:
        with open(file_path, encoding="utf-8") as handle:
            parsed = json.load(handle)
        return {int(key): float(value) for key, value in parsed.items()}
    if raw:
        parsed = json.loads(raw)
        return {int(key): float(value) for key, value in parsed.items()}
    return None


def _load_json_series_map(raw: str | None, file_path: str | None) -> dict | None:
    if file_path:
        with open(file_path, encoding="utf-8") as handle:
            parsed = json.load(handle)
        return {
            int(key): [float(item) for item in value]
            for key, value in parsed.items()
            if isinstance(value, list)
        }
    if raw:
        parsed = json.loads(raw)
        return {
            int(key): [float(item) for item in value]
            for key, value in parsed.items()
            if isinstance(value, list)
        }
    return None


def _load_json_percent_map(raw: str | None, file_path: str | None) -> dict[int, float] | None:
    if file_path:
        with open(file_path, encoding="utf-8") as handle:
            parsed = json.load(handle)
        return {int(key): float(value) for key, value in parsed.items()}
    if raw:
        parsed = json.loads(raw)
        return {int(key): float(value) for key, value in parsed.items()}
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description="River network forecast bridge service")
    parser.add_argument("--json", action="store_true", help="Print JSON result to stdout")
    parser.add_argument("--hours", type=float, default=DEFAULT_FORECAST_HOURS, help="Forecast duration in hours")
    parser.add_argument("--steps", type=int, help="Number of time steps (overrides --hours when set)")
    parser.add_argument("--dt", type=float, default=DEFAULT_DT, help="Time step size in seconds")
    parser.add_argument("--boundary-json", type=str, help="JSON map of boundary node values for simulation")
    parser.add_argument("--boundary-file", type=str, help="Path to JSON file with boundary node values")
    parser.add_argument("--boundary-series-file", type=str, help="Path to JSON file with boundary time series")
    parser.add_argument("--boundary-series-json", type=str, help="JSON map of boundary time series for simulation")
    parser.add_argument("--node-heads-json", type=str, help="JSON map of initial node heads for simulation")
    parser.add_argument("--node-heads-file", type=str, help="Path to JSON file with initial node heads")
    parser.add_argument("--gate-openings-file", type=str, help="Path to JSON file with gate opening percents")
    parser.add_argument("--gate-openings-json", type=str, help="JSON map of gate opening percents (0-100)")
    parser.add_argument("--simulation-start-at", type=str, help="Simulation start time ISO string")
    args = parser.parse_args()

    try:
        boundary_values = _load_json_map(args.boundary_json, args.boundary_file)
        boundary_series = _load_json_series_map(args.boundary_series_json, args.boundary_series_file)
        initial_node_heads = _load_json_map(args.node_heads_json, args.node_heads_file)
        gate_openings = _load_json_percent_map(args.gate_openings_json, args.gate_openings_file)

        if args.steps is not None:
            payload = run_forecast(
                n_steps=max(1, args.steps),
                dt=args.dt,
                boundary_values=boundary_values,
                boundary_series=boundary_series,
                initial_node_heads_override=initial_node_heads,
                simulation_start_at=args.simulation_start_at,
                gate_openings=gate_openings,
            )
        else:
            payload = run_forecast(
                forecast_hours=args.hours,
                dt=args.dt,
                boundary_values=boundary_values,
                boundary_series=boundary_series,
                initial_node_heads_override=initial_node_heads,
                simulation_start_at=args.simulation_start_at,
                gate_openings=gate_openings,
            )
        if args.json:
            sys.stdout.write(json.dumps(payload, ensure_ascii=False))
        return 0
    except Exception as exc:
        if args.json:
            sys.stdout.write(
                json.dumps({"status": "error", "message": str(exc)}, ensure_ascii=False)
            )
        else:
            print(f"river network forecast failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
