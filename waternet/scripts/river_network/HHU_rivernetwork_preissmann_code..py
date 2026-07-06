# ============================================================
# 本程序可用于计算无工程矩形河网
# 版权说明：1、本程序特服务于河海大学水电院智慧水利本科生教学。
#          2、本程序允许本校本院本科生进行二次开发，用于完成课程设计、毕业设计等学业相关任务。
#          3、基于本程序的科学研究用途请标明出处：“河海大学水电院《数字孪生水网教学团队》”
#          4、本程序及其二次开发版本禁止任何商业用途。
# 联系方式：许栋：xudong@hhu.edu.cn;
#          徐津：hhu_xj@hhu.edu.cn;
# ============================================================

from __future__ import annotations
from dataclasses import dataclass
from typing import Any, Callable, Dict, List, Optional, Sequence, Tuple, Union
from pathlib import Path
import math
import numpy as np
Number = Union[int, float]
TimeValue = Union[Number, Sequence[Number], np.ndarray, Callable[[float, int], Number]]

# ============================================================
# 数据结构
# ============================================================

@dataclass
class BoundaryCondition:
    """
    type:
        - 'stage' : 给定边界水位 Z(t)
        - 'flow'  : 给定边界流量 Q(t)
    value:
        - 常数
        - 长度 >= n_steps+1 的序列
        - 可调用对象 f(t, step)

    约定：流量正方向与河段 start_node -> end_node 的方向一致。
    """
    type: str
    value: TimeValue

@dataclass
class ReachData:
    reach_id: int
    start_node: int
    end_node: int
    width: float
    bed: Union[float, Tuple[float, float], Sequence[float], np.ndarray]
    length: float
    dx: float
    chezy: float
    beta: float = 1.0
    lateral_inflow: float = 0.0  # 按每单位长度侧向入流 q_l (m^2/s)

@dataclass
class ReachState:
    q: np.ndarray  # 各断面流量 (m^3/s)
    z: np.ndarray  # 各断面水位 (m)

    def copy(self) -> "ReachState":
        return ReachState(self.q.copy(), self.z.copy())

# ============================================================
# 工具函数
# ============================================================

def eval_time_value(spec: TimeValue, t: float, step: int) -> float:
    if callable(spec):
        return float(spec(t, step))
    if isinstance(spec, (list, tuple, np.ndarray)):
        arr = np.asarray(spec, dtype=float)
        if step >= len(arr):
            raise IndexError(f"时步索引 {step} 超出边界条件序列长度 {len(arr)}")
        return float(arr[step])
    return float(spec)


def _count_nonzero_rowwise(a: np.ndarray) -> np.ndarray:
    return np.count_nonzero(np.abs(a) > 0.0, axis=1)

# ============================================================
# 单河段：矩形断面 + 四点隐式线性化 + 追赶系数法
# ============================================================

class RectangularReachPreissmannChase:
    """
    说明
    ----
    基于PPT给出的四点离散线性方程：
        Q_{j+1} - Q_j + C_j z_{j+1} + C_j z_j = D_j
        E_j Q_j + G_j Q_{j+1} + F_j z_{j+1} - F_j z_j = Phi_j
    然后按边界类型采用两套追赶关系：
    1) 起点给流量（或选择以 z_N 作为末端消元变量）
       Q_j = P_j - V_j z_j
       z_j = S_{j+1} - T_{j+1} z_{j+1}
    2) 起点给水位（或选择以 Q_N 作为末端消元变量）
       z_j = P_j - V_j Q_j
       Q_j = S_{j+1} - T_{j+1} Q_{j+1}
    注意
    ----
    - 追赶公式沿河段离散编号 0 -> N 使用，也就是 start_node -> end_node 方向。
    - 实际流量可以为负，不要求“物理上总是顺流”。
    - 这里的线性化系数均采用上一个时层 old_state计算，就是PPT中所指的n时刻。
    """
    def __init__(
        self,
        reach: ReachData,
        g: float = 9.81,
        theta: float = 0.65,
        min_depth: float = 1.0e-4,
        denom_eps: float = 1.0e-12,
    ) -> None:
        self.data = reach
        self.g = float(g)
        self.theta = float(theta)
        self.min_depth = float(min_depth)
        self.denom_eps = float(denom_eps)
        n_cells = max(1, int(round(reach.length / reach.dx)))
        self.n_cells = n_cells
        self.n_nodes = n_cells + 1
        self.dx = reach.length / n_cells
        self.width = float(reach.width)
        self.chezy = float(reach.chezy)
        self.beta = float(reach.beta)
        self.lateral_inflow = float(reach.lateral_inflow)
        self.bed = self._build_bed_profile(reach.bed)

    def _build_bed_profile(
        self,
        bed: Union[float, Tuple[float, float], Sequence[float], np.ndarray],
    ) -> np.ndarray:
        if isinstance(bed, (int, float)):
            return np.full(self.n_nodes, float(bed), dtype=float)
        arr = np.asarray(bed, dtype=float)
        if arr.ndim == 0:
            return np.full(self.n_nodes, float(arr), dtype=float)
        if arr.size == 2:
            return np.linspace(float(arr[0]), float(arr[1]), self.n_nodes)
        if arr.size == self.n_nodes:
            return arr.astype(float).copy()
        raise ValueError(
            f"reach {self.data.reach_id} 的 bed 输入必须是标量、长度为2的(上/下游)元组，"
            f"或长度为 {self.n_nodes} 的断面底高程序列。"
        )

    def depth(self, z: np.ndarray) -> np.ndarray:
        return np.maximum(z - self.bed, self.min_depth)

    def area(self, z: np.ndarray) -> np.ndarray:
        return self.width * self.depth(z)

    def wetted_perimeter(self, z: np.ndarray) -> np.ndarray:
        return self.width + 2.0 * self.depth(z)

    def hydraulic_radius(self, z: np.ndarray) -> np.ndarray:
        a = self.area(z)
        p = self.wetted_perimeter(z)
        return a / np.maximum(p, 1.0e-12)

    def _safe_denom(self, x: float) -> float:
        if abs(x) >= self.denom_eps:
            return x
        return self.denom_eps if x >= 0.0 else -self.denom_eps

    def _stage_bc_value(self, spec: TimeValue, sec_idx: int, t: float, step: int) -> float:
        return max(eval_time_value(spec, t, step), self.bed[sec_idx] + self.min_depth)

    def _build_linear_coefficients(
        self,
        old_state: ReachState,
        dt: float,
    ) -> Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
        """
        根据 old_state 计算四点离散线性系数 C, D, E, G, F, Phi。
        """
        qn = old_state.q.copy()
        zn = np.maximum(old_state.z.copy(), self.bed + self.min_depth)
        A = self.area(zn)
        R = self.hydraulic_radius(zn)
        u = qn / np.maximum(A, 1.0e-12)
        C = np.zeros(self.n_cells, dtype=float)
        D = np.zeros(self.n_cells, dtype=float)
        E = np.zeros(self.n_cells, dtype=float)
        G = np.zeros(self.n_cells, dtype=float)
        F = np.zeros(self.n_cells, dtype=float)
        Phi = np.zeros(self.n_cells, dtype=float)
        th = self.theta
        for j in range(self.n_cells):
            Bp = self.width
            qp = self.lateral_inflow
            C[j] = Bp * self.dx / (2.0 * th * dt)
            D[j] = (
                qp * self.dx / th
                - (1.0 - th) / th * (qn[j + 1] - qn[j])
                + C[j] * (zn[j + 1] + zn[j])
            )
            # 追系数
            u_beta_j = self.beta * u[j]
            u_beta_j1 = self.beta * u[j + 1]
            fric_j = (
                self.g * abs(u[j]) * self.dx
                / (2.0 * th * (self.chezy ** 2) * max(R[j], self.min_depth))
            )
            fric_j1 = (
                self.g * abs(u[j + 1]) * self.dx
                / (2.0 * th * (self.chezy ** 2) * max(R[j + 1], self.min_depth))
            )
            E[j] = self.dx / (2.0 * th * dt) - u_beta_j + fric_j
            G[j] = self.dx / (2.0 * th * dt) + u_beta_j1 + fric_j1
            F[j] = self.g * 0.5 * (A[j] + A[j + 1])
            uq_j = self.beta * u[j] * qn[j]
            uq_j1 = self.beta * u[j + 1] * qn[j + 1]
            Phi[j] = (
                self.dx / (2.0 * th * dt) * (qn[j + 1] + qn[j])
                - (1.0 - th) / th * (uq_j1 - uq_j)
                - (1.0 - th) / th * F[j] * (zn[j + 1] - zn[j])
            )
        return C, D, E, G, F, Phi

    def _forward_sweep_flow_start(
        self,
        old_state: ReachState,
        dt: float,
        q0: float,
    ) -> Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
        """
        起点给流量（或用它来构造以 z_N 为末端变量的关系）：
            Q_j = P_j - V_j z_j
            z_j = S_{j+1} - T_{j+1} z_{j+1}
        """
        C, D, E, G, F, Phi = self._build_linear_coefficients(old_state, dt)
        N = self.n_cells
        P = np.zeros(N + 1, dtype=float)
        V = np.zeros(N + 1, dtype=float)
        S = np.zeros(N + 1, dtype=float)
        T = np.zeros(N + 1, dtype=float)
        P[0] = q0
        V[0] = 0.0
        for j in range(N):
            Y1 = V[j] + C[j]
            Y2 = F[j] + E[j] * V[j]
            Y3 = D[j] + P[j]
            Y4 = Phi[j] - E[j] * P[j]
            den = self._safe_denom(Y1 * G[j] + Y2)
            T[j + 1] = (C[j] * G[j] - F[j]) / den
            S[j + 1] = (G[j] * Y3 - Y4) / den
            P[j + 1] = Y3 - Y1 * S[j + 1]
            V[j + 1] = C[j] - Y1 * T[j + 1]
        return P, V, S, T

    def _forward_sweep_stage_start(
        self,
        old_state: ReachState,
        dt: float,
        z0: float,
    ) -> Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
        """
        起点给水位（或用它来构造以 Q_N 为末端变量的关系）：
            z_j = P_j - V_j Q_j
            Q_j = S_{j+1} - T_{j+1} Q_{j+1}
        """
        C, D, E, G, F, Phi = self._build_linear_coefficients(old_state, dt)
        N = self.n_cells
        P = np.zeros(N + 1, dtype=float)
        V = np.zeros(N + 1, dtype=float)
        S = np.zeros(N + 1, dtype=float)
        T = np.zeros(N + 1, dtype=float)
        P[0] = z0
        V[0] = 0.0
        for j in range(N):
            Y1 = D[j] - C[j] * P[j]
            Y2 = Phi[j] + F[j] * P[j]
            Y3 = 1.0 + C[j] * V[j]
            Y4 = E[j] + F[j] * V[j]
            den = self._safe_denom(F[j] * Y3 + C[j] * Y4)
            T[j + 1] = (C[j] * G[j] - F[j]) / den
            S[j + 1] = (C[j] * Y2 - F[j] * Y1) / den
            P[j + 1] = (Y1 + Y3 * S[j + 1]) / self._safe_denom(C[j])
            V[j + 1] = (Y3 * T[j + 1] + 1.0) / self._safe_denom(C[j])
        return P, V, S, T

    def solve_timestep(
        self,
        old_state: ReachState,
        bc_start: BoundaryCondition,
        bc_end: BoundaryCondition,
        dt: float,
        t_new: float,
        step: int,
    ) -> ReachState:
        """
        支持四种边界组合：
        1) flow - stage : 经典“上游流量、下游水位”
        2) stage - flow : 经典“上游水位、下游流量”
        3) stage - stage: 由起点水位递推，终点用已知水位反求 Q_N
        4) flow - flow  : 由起点流量递推，终点用已知流量反求 z_N
        """
        N = self.n_cells
        q = np.zeros(N + 1, dtype=float)
        z = np.zeros(N + 1, dtype=float)
        if bc_start.type == "flow":
            q0 = eval_time_value(bc_start.value, t_new, step)
            P, V, S, T = self._forward_sweep_flow_start(old_state, dt, q0)
            if bc_end.type == "stage":
                z[N] = self._stage_bc_value(bc_end.value, N, t_new, step)
                q[N] = P[N] - V[N] * z[N]
            elif bc_end.type == "flow":
                q[N] = eval_time_value(bc_end.value, t_new, step)
                z[N] = (P[N] - q[N]) / self._safe_denom(V[N])
                z[N] = max(z[N], self.bed[N] + self.min_depth)
                q[N] = P[N] - V[N] * z[N]
            else:
                raise ValueError("bc_end.type 必须是 'stage' 或 'flow'")
            for j in range(N - 1, -1, -1):
                z[j] = S[j + 1] - T[j + 1] * z[j + 1]
                z[j] = max(z[j], self.bed[j] + self.min_depth)
                q[j] = P[j] - V[j] * z[j]
        elif bc_start.type == "stage":
            z[0] = self._stage_bc_value(bc_start.value, 0, t_new, step)
            P, V, S, T = self._forward_sweep_stage_start(old_state, dt, z[0])
            if bc_end.type == "flow":
                q[N] = eval_time_value(bc_end.value, t_new, step)
                z[N] = P[N] - V[N] * q[N]
            elif bc_end.type == "stage":
                z[N] = self._stage_bc_value(bc_end.value, N, t_new, step)
                q[N] = (P[N] - z[N]) / self._safe_denom(V[N])
            else:
                raise ValueError("bc_end.type 必须是 'stage' 或 'flow'")
            z[N] = max(z[N], self.bed[N] + self.min_depth)
            for j in range(N - 1, -1, -1):
                q[j] = S[j + 1] - T[j + 1] * q[j + 1]
                z[j] = P[j] - V[j] * q[j]
                z[j] = max(z[j], self.bed[j] + self.min_depth)
        else:
            raise ValueError("bc_start.type 必须是 'stage' 或 'flow'")
        return ReachState(q=q, z=z)

# ============================================================
# 河网求解器：外层迭代节点水位（Newton），内层用追赶系数法解河段
# ============================================================

class RiverNetworkPreissmannSolver:
    """
    说明
    ----
    1. 河段：用四点隐式线性化 + 追赶系数法。
    2. 河网层面：对“内部节点水位”做外层 Newton 迭代。
    3. node_matrix 表示“节点-节点邻接矩阵”,定义与PPT一致：
       - shape = (n_nodes, n_nodes)
       - 若节点 i 与节点 j 之间存在河道相连，则 node_matrix[i, j] = node_matrix[j, i] = 1
       - 若不相连，则为 0
       - 对角线必须为 0
       - 若某节点所在行（或列）非零元素个数为 1，则视为边界节点
    4. 矩形断面；糙率用谢才系数 C。
    5. 默认任意两个节点之间至多一条河段；若同一对节点之间有多条并行河段，
       则这种 node_matrix 无法表达，程序会报错。
    """
    def __init__(
        self,
        reach_table: Sequence[Union[ReachData, Dict[str, Any]]],
        node_matrix: np.ndarray,
        boundary_conditions: Dict[int, Dict[str, Any]],
        dt: float,
        theta: float = 0.65,
        g: float = 9.81,
        initial_node_heads: Optional[Dict[int, float]] = None,
        node_sources: Optional[Dict[int, TimeValue]] = None,
        gate_nodes: Optional[Dict[int, Dict[str, Any]]] = None,
        node_ids: Optional[Sequence[int]] = None,
        default_initial_depth: float = 2.0,
        min_depth: float = 1.0e-4,
        outer_tol: float = 1.0e-6,
        outer_maxiter: int = 20,
        outer_fd_eps: float = 1.0e-4,
        outer_relaxation: float = 1.0,
        denom_eps: float = 1.0e-12,
    ) -> None:
        self.dt = float(dt)
        self.theta = float(theta)
        self.g = float(g)
        self.default_initial_depth = float(default_initial_depth)
        self.min_depth = float(min_depth)
        self.outer_tol = float(outer_tol)
        self.outer_maxiter = int(outer_maxiter)
        self.outer_fd_eps = float(outer_fd_eps)
        self.outer_relaxation = float(outer_relaxation)
        self.denom_eps = float(denom_eps)
        self.node_matrix = np.asarray(node_matrix, dtype=float)
        self._validate_and_normalize_node_matrix()
        self.n_nodes = self.node_matrix.shape[0]
        self.external_node_ids = self._resolve_node_ids(reach_table, node_ids)
        self.id_to_index = {nid: i for i, nid in enumerate(self.external_node_ids)}
        self.index_to_id = {i: nid for i, nid in enumerate(self.external_node_ids)}
        self.reaches: List[RectangularReachPreissmannChase] = []
        
        for i, item in enumerate(reach_table):
            data = item if isinstance(item, ReachData) else ReachData(**item)
            if data.reach_id != i:
                # 内部强制按输入顺序编号
                data = ReachData(
                    reach_id=i,
                    start_node=data.start_node,
                    end_node=data.end_node,
                    width=data.width,
                    bed=data.bed,
                    length=data.length,
                    dx=data.dx,
                    chezy=data.chezy,
                    beta=data.beta,
                    lateral_inflow=data.lateral_inflow,
                )
            if data.start_node not in self.id_to_index or data.end_node not in self.id_to_index:
                raise ValueError(f"reach {i} 的 start/end 节点编号未在 node_ids 或 node_matrix 中定义")
            self.reaches.append(
                RectangularReachPreissmannChase(
                    data,
                    g=self.g,
                    theta=self.theta,
                    min_depth=self.min_depth,
                    denom_eps=self.denom_eps,
                )
            )
        self.n_reaches = len(self.reaches)
        # 校验 node_matrix 与 reach_table 是否一致
        self._validate_node_matrix_against_reaches()
        # 按节点度数识别边界/内部节点
        degree = _count_nonzero_rowwise(self.node_matrix)
        isolated = np.flatnonzero(degree == 0)
        if len(isolated) > 0:
            raise ValueError(
                f"node_matrix 中存在孤立节点（整行/列全0）: "
                f"{[self.index_to_id[i] for i in isolated.tolist()]}"
            )
        self.boundary_indices = [i for i, c in enumerate(degree) if c == 1]
        self.internal_indices = [i for i, c in enumerate(degree) if c > 1]
        if not self.internal_indices:
            raise ValueError("未识别到内部节点（node_matrix 中非零个数 > 1 的行）")
        self.boundary_conditions: Dict[int, BoundaryCondition] = {}
        for nid, spec in boundary_conditions.items():
            if nid not in self.id_to_index:
                raise ValueError(f"边界条件里出现未知节点编号: {nid}")
            bc_type = str(spec["type"]).strip().lower()
            if bc_type not in ("stage", "flow"):
                raise ValueError(f"节点 {nid} 的边界 type 只能是 'stage' 或 'flow'")
            self.boundary_conditions[self.id_to_index[nid]] = BoundaryCondition(bc_type, spec["value"])
        missing_bc = [self.index_to_id[i] for i in self.boundary_indices if i not in self.boundary_conditions]
        if missing_bc:
            raise ValueError(f"以下边界节点未提供 boundary_conditions: {missing_bc}")
        if not any(bc.type == "stage" for bc in self.boundary_conditions.values()):
            raise ValueError("建议至少给一个 stage 边界；否则整体水位基准可能漂移。")
        self.initial_node_heads_input = initial_node_heads or {}
        self.node_sources_input = node_sources or {}
        self.gate_nodes_input = gate_nodes or {}
        self.node_min_heads = np.array(
            [self._incident_bed_min(i) + self.min_depth for i in range(self.n_nodes)],
            dtype=float,
        )
        self.current_reach_states: List[ReachState] = self._build_initial_reach_states()
        self.current_node_heads: np.ndarray = self._build_initial_node_heads_from_states(t=0.0, step=0)

    # -------------------- 初始化与校验 --------------------

    def _resolve_node_ids(
        self,
        reach_table: Sequence[Union[ReachData, Dict[str, Any]]],
        node_ids: Optional[Sequence[int]],
    ) -> List[int]:
        if node_ids is not None:
            ids = list(node_ids)
            if len(ids) != self.n_nodes:
                raise ValueError("传入 node_ids 时，其长度必须等于 node_matrix 的行数")
            return ids
        # 自动推断 0-based / 1-based
        used = set()
        for item in reach_table:
            d = item if isinstance(item, ReachData) else ReachData(**item)
            used.add(int(d.start_node))
            used.add(int(d.end_node))
        one_based = all(1 <= nid <= self.n_nodes for nid in used) and (0 not in used)
        if one_based:
            return list(range(1, self.n_nodes + 1))
        return list(range(self.n_nodes))

    def _validate_node_matrix_against_reaches(self) -> None:
        """
        校验规则：
        1. reach_table 中每条河段 (start_node, end_node) 在 node_matrix 中必须对应一个 1
        2. node_matrix 中每一个 1（上三角）都必须在 reach_table 中有对应河段
        """
        reach_pairs = {}
        for k, reach in enumerate(self.reaches):
            s = self.id_to_index[reach.data.start_node]
            e = self.id_to_index[reach.data.end_node]
            if s == e:
                raise ValueError(f"reach {k} 的 start_node 和 end_node 不能相同")
            key = tuple(sorted((s, e)))
            if key in reach_pairs:
                i0, j0 = key
                raise ValueError(
                    f"节点对 ({self.index_to_id[i0]}, {self.index_to_id[j0]}) 之间存在多条河段，"
                    f"但当前 node_matrix 是 0/1 邻接矩阵，无法表达并行重边。"
                )
            reach_pairs[key] = k
            if self.node_matrix[s, e] != 1.0:
                raise ValueError(
                    f"reach {k} 连接节点 ({reach.data.start_node}, {reach.data.end_node})，"
                    f"但 node_matrix[{s}, {e}] / node_matrix[{e}, {s}] 不是 1"
                )
        matrix_pairs = set()
        for i in range(self.n_nodes):
            for j in range(i + 1, self.n_nodes):
                if self.node_matrix[i, j] == 1.0:
                    matrix_pairs.add((i, j))
        reach_pair_set = set(reach_pairs.keys())
        if matrix_pairs != reach_pair_set:
            msg = []
            only_matrix = matrix_pairs - reach_pair_set
            only_reaches = reach_pair_set - matrix_pairs
            if only_matrix:
                msg.append(
                    "node_matrix 中有连接但 reach_table 未定义: "
                    + str([(self.index_to_id[i], self.index_to_id[j]) for i, j in sorted(only_matrix)])
                )
            if only_reaches:
                msg.append(
                    "reach_table 中有河段但 node_matrix 未标 1: "
                    + str([(self.index_to_id[i], self.index_to_id[j]) for i, j in sorted(only_reaches)])
                )
            raise ValueError("；".join(msg))

    def _incident_bed_min(self, node_index: int) -> float:
        vals = []
        for reach in self.reaches:
            s = self.id_to_index[reach.data.start_node]
            e = self.id_to_index[reach.data.end_node]
            if s == node_index:
                vals.append(float(reach.bed[0]))
            if e == node_index:
                vals.append(float(reach.bed[-1]))
        if not vals:
            return 0.0
        return min(vals)

    def _node_source(self, node_index: int, t: float, step: int) -> float:
        ext_id = self.index_to_id[node_index]
        spec = self.node_sources_input.get(ext_id, 0.0)
        return eval_time_value(spec, t, step)

    def _gate_discharge(self, node_index: int, head: float) -> float:
        import math

        ext_id = self.index_to_id[node_index]
        spec = self.gate_nodes_input.get(ext_id)
        if not spec:
            return 0.0
        opening_ratio = float(spec.get("opening_ratio", 1.0))
        if opening_ratio <= 0.0:
            return 0.0
        bed_min = self._incident_bed_min(node_index)
        water_depth = max(float(head) - bed_min, self.min_depth)
        if water_depth <= self.min_depth:
            return 0.0
        e = opening_ratio * water_depth
        mu = max(0.1, min(0.6, 0.60 - 0.176 * opening_ratio))
        sqrt_term = math.sqrt(2.0 * self.g * water_depth)
        total = 0.0
        for width in spec.get("widths", []):
            total += mu * float(width) * e * sqrt_term
        return total

    def _initial_head_for_node(self, node_index: int, t0: float, step0: int) -> float:
        ext_id = self.index_to_id[node_index]
        if node_index in self.boundary_conditions and self.boundary_conditions[node_index].type == "stage":
            return max(
                eval_time_value(self.boundary_conditions[node_index].value, t0, step0),
                self.node_min_heads[node_index],
            )
        if ext_id in self.initial_node_heads_input:
            return max(float(self.initial_node_heads_input[ext_id]), self.node_min_heads[node_index])
        return self.node_min_heads[node_index] + self.default_initial_depth

    def _build_initial_reach_states(self) -> List[ReachState]:
        t0, step0 = 0.0, 0
        node_h0 = np.zeros(self.n_nodes, dtype=float)
        for i in range(self.n_nodes):
            node_h0[i] = self._initial_head_for_node(i, t0, step0)
        states: List[ReachState] = []
        for reach in self.reaches:
            s = self.id_to_index[reach.data.start_node]
            e = self.id_to_index[reach.data.end_node]
            z0 = np.linspace(node_h0[s], node_h0[e], reach.n_nodes)
            z0 = np.maximum(z0, reach.bed + self.min_depth)
            q_start: Optional[float] = None
            q_end: Optional[float] = None
            if s in self.boundary_conditions and self.boundary_conditions[s].type == "flow":
                q_start = eval_time_value(self.boundary_conditions[s].value, t0, step0)
            if e in self.boundary_conditions and self.boundary_conditions[e].type == "flow":
                q_end = eval_time_value(self.boundary_conditions[e].value, t0, step0)
            if q_start is not None and q_end is not None:
                q0_scalar = 0.5 * (q_start + q_end)
            elif q_start is not None:
                q0_scalar = q_start
            elif q_end is not None:
                q0_scalar = q_end
            else:
                q0_scalar = 0.0
            q0 = np.full(reach.n_nodes, q0_scalar, dtype=float)
            states.append(ReachState(q=q0, z=z0))
        return states

    def _build_initial_node_heads_from_states(self, t: float, step: int) -> np.ndarray:
        heads = np.zeros(self.n_nodes, dtype=float)
        for i in range(self.n_nodes):
            if i in self.boundary_conditions and self.boundary_conditions[i].type == "stage":
                heads[i] = max(
                    eval_time_value(self.boundary_conditions[i].value, t, step),
                    self.node_min_heads[i],
                )
            else:
                heads[i] = self._initial_head_for_node(i, 0.0, 0)
        # 用河段端点进一步覆盖流量边界节点的水位
        for reach, st in zip(self.reaches, self.current_reach_states):
            s = self.id_to_index[reach.data.start_node]
            e = self.id_to_index[reach.data.end_node]
            if s not in self.boundary_conditions or self.boundary_conditions[s].type != "stage":
                heads[s] = st.z[0]
            if e not in self.boundary_conditions or self.boundary_conditions[e].type != "stage":
                heads[e] = st.z[-1]
        return heads

    def _clip_internal_heads(self, internal_heads: np.ndarray) -> np.ndarray:
        out = internal_heads.copy()
        for loc, idx in enumerate(self.internal_indices):
            out[loc] = max(out[loc], self.node_min_heads[idx])
        return out

    # -------------------- 河网残差与 Jacobian --------------------

    def _solve_all_reaches_for_node_heads(
        self,
        internal_heads: np.ndarray,
        old_states: List[ReachState],
        t_new: float,
        step: int,
    ) -> Tuple[np.ndarray, List[ReachState]]:
        """
        给定内部节点水位，逐河段调用追赶系数法，得到河网节点连续方程残差：
            residual[node] = 节点流出总量 - 节点外加来流
        对应PPT第3、4、5步
        """
        internal_heads = self._clip_internal_heads(internal_heads)
        node_heads = self.current_node_heads.copy()
        for loc, idx in enumerate(self.internal_indices):
            node_heads[idx] = internal_heads[loc]
        for idx in self.boundary_indices:
            if self.boundary_conditions[idx].type == "stage":
                node_heads[idx] = max(
                    eval_time_value(self.boundary_conditions[idx].value, t_new, step),
                    self.node_min_heads[idx],
                )
        residual = np.zeros(len(self.internal_indices), dtype=float)
        internal_map = {idx: k for k, idx in enumerate(self.internal_indices)}
        new_states: List[ReachState] = []
        for reach, old_state in zip(self.reaches, old_states):
            s = self.id_to_index[reach.data.start_node]
            e = self.id_to_index[reach.data.end_node]
            if s in self.internal_indices:
                bc_s = BoundaryCondition("stage", float(node_heads[s]))
            else:
                bc_s = self.boundary_conditions[s]
            if e in self.internal_indices:
                bc_e = BoundaryCondition("stage", float(node_heads[e]))
            else:
                bc_e = self.boundary_conditions[e]
            st = reach.solve_timestep(
                old_state=old_state,
                bc_start=bc_s,
                bc_end=bc_e,
                dt=self.dt,
                t_new=t_new,
                step=step,
            )
            new_states.append(st)
            # 节点残差：定义为“离开节点的总流量 - 节点外加来流”
            if s in internal_map:
                residual[internal_map[s]] += st.q[0]
            if e in internal_map:
                residual[internal_map[e]] += -st.q[-1]
        for idx in self.internal_indices:
            residual[internal_map[idx]] -= self._node_source(idx, t_new, step)
            residual[internal_map[idx]] += self._gate_discharge(idx, node_heads[idx])
        return residual, new_states

    def _build_jacobian_fd(
        self,
        internal_heads: np.ndarray,
        base_residual: np.ndarray,
        old_states: List[ReachState],
        t_new: float,
        step: int,
    ) -> np.ndarray:
        n = len(internal_heads)
        J = np.zeros((n, n), dtype=float)
        for j in range(n):
            hp = internal_heads.copy()
            dh = self.outer_fd_eps * max(1.0, abs(hp[j]))
            hp[j] += dh
            rp, _ = self._solve_all_reaches_for_node_heads(
                internal_heads=hp,
                old_states=old_states,
                t_new=t_new,
                step=step,
            )
            J[:, j] = (rp - base_residual) / dh
        return J

    def _assemble_full_node_heads(
        self,
        internal_heads: np.ndarray,
        reach_states: List[ReachState],
        t_new: float,
        step: int,
    ) -> np.ndarray:
        internal_heads = self._clip_internal_heads(internal_heads)
        heads = self.current_node_heads.copy()
        for loc, idx in enumerate(self.internal_indices):
            heads[idx] = internal_heads[loc]
        for idx in self.boundary_indices:
            if self.boundary_conditions[idx].type == "stage":
                heads[idx] = max(
                    eval_time_value(self.boundary_conditions[idx].value, t_new, step),
                    self.node_min_heads[idx],
                )
        # 流量边界节点的水位由外伸边界河段端点算得
        for reach, st in zip(self.reaches, reach_states):
            s = self.id_to_index[reach.data.start_node]
            e = self.id_to_index[reach.data.end_node]
            if s in self.boundary_indices and self.boundary_conditions[s].type == "flow":
                heads[s] = st.z[0]
            if e in self.boundary_indices and self.boundary_conditions[e].type == "flow":
                heads[e] = st.z[-1]
        return heads

    def solve(
        self,
        n_steps: int,
        save_full_reach_history: bool = True,
    ) -> Dict[str, Any]:
        times = np.arange(n_steps + 1, dtype=float) * self.dt
        node_head_history = np.zeros((n_steps + 1, self.n_nodes), dtype=float)
        node_head_history[0, :] = self.current_node_heads.copy()
        if save_full_reach_history:
            reach_history_q: List[List[np.ndarray]] = [[st.q.copy() for st in self.current_reach_states]]
            reach_history_z: List[List[np.ndarray]] = [[st.z.copy() for st in self.current_reach_states]]
        else:
            reach_history_q = []
            reach_history_z = []
        for step in range(1, n_steps + 1):
            t_new = times[step]
            old_states = [st.copy() for st in self.current_reach_states]
            internal_heads = np.array([self.current_node_heads[i] for i in self.internal_indices], dtype=float)
            converged = False
            last_res_norm = np.inf
            for _outer in range(self.outer_maxiter):
                residual, reach_states = self._solve_all_reaches_for_node_heads(
                    internal_heads=internal_heads,
                    old_states=old_states,
                    t_new=t_new,
                    step=step,
                )
                res_norm = np.linalg.norm(residual, ord=np.inf)
                last_res_norm = res_norm

                if res_norm < self.outer_tol:
                    converged = True
                    self.current_reach_states = [st.copy() for st in reach_states]
                    self.current_node_heads = self._assemble_full_node_heads(internal_heads, reach_states, t_new, step)
                    break
                J = self._build_jacobian_fd(
                    internal_heads=internal_heads,
                    base_residual=residual,
                    old_states=old_states,
                    t_new=t_new,
                    step=step,
                )
                try:
                    dH = np.linalg.solve(J, -residual)
                except np.linalg.LinAlgError:
                    dH = np.linalg.lstsq(J, -residual, rcond=None)[0]
                alpha = min(1.0, self.outer_relaxation)
                accepted = False
                for _ls in range(12):
                    trial_heads = self._clip_internal_heads(internal_heads + alpha * dH)
                    trial_residual, trial_states = self._solve_all_reaches_for_node_heads(
                        internal_heads=trial_heads,
                        old_states=old_states,
                        t_new=t_new,
                        step=step,
                    )
                    if np.linalg.norm(trial_residual, ord=np.inf) < res_norm:
                        internal_heads = trial_heads
                        accepted = True
                        break
                    alpha *= 0.5
                if not accepted:
                    internal_heads = self._clip_internal_heads(internal_heads + 0.2 * dH)
            if not converged:
                raise RuntimeError(
                    f"时步 {step} 的外层节点水位迭代未收敛；最后残差无穷范数 = {last_res_norm:.3e}\n"
                    f"建议：减小 dt、提高初始水位质量、适当增大糙率、或检查边界条件与河网拓扑。"
                )
            node_head_history[step, :] = self.current_node_heads.copy()
            if save_full_reach_history:
                reach_history_q.append([st.q.copy() for st in self.current_reach_states])
                reach_history_z.append([st.z.copy() for st in self.current_reach_states])
            if step % 5 == 0 or step == n_steps:
                print(f"step = {step}, t = {t_new:.2f} s")
        result = {
            "times": times,
            "node_ids": self.external_node_ids,
            "internal_node_ids": [self.index_to_id[i] for i in self.internal_indices],
            "boundary_node_ids": [self.index_to_id[i] for i in self.boundary_indices],
            "node_head_history": node_head_history,
            "final_reach_states": [st.copy() for st in self.current_reach_states],
        }
        if save_full_reach_history:
            result["reach_history_q"] = reach_history_q
            result["reach_history_z"] = reach_history_z
        return result
    def _validate_and_normalize_node_matrix(self) -> None:
        if self.node_matrix.ndim != 2:
            raise ValueError("node_matrix 必须是二维数组")
        if self.node_matrix.shape[0] != self.node_matrix.shape[1]:
            raise ValueError("node_matrix 必须是节点-节点邻接矩阵，因此必须为方阵")
        if not np.allclose(self.node_matrix, self.node_matrix.T):
            raise ValueError("node_matrix 必须是对称矩阵")
        if not np.allclose(np.diag(self.node_matrix), 0.0):
            raise ValueError("node_matrix 对角线必须为 0")
        valid_mask = np.isclose(self.node_matrix, 0.0) | np.isclose(self.node_matrix, 1.0)
        if not np.all(valid_mask):
            raise ValueError("node_matrix 只能包含 0 和 1")
        # 归一化成严格的 0/1
        self.node_matrix = (self.node_matrix > 0.5).astype(float)

# ============================================================
# 输入输出工具
# ============================================================

def load_reach_table_from_csv(path: Union[str, Path]) -> List[Dict[str, Any]]:
    import pandas as pd

    df = pd.read_csv(path)
    required = {"start_node", "end_node", "width", "bed", "length", "dx", "chezy"}
    missing = required - set(df.columns)
    if missing:
        raise ValueError(f"河段表缺少必要列: {sorted(missing)}")
    records = df.to_dict("records")
    for i, row in enumerate(records):
        row["start_node"] = int(row["start_node"])
        row["end_node"] = int(row["end_node"])
        if "reach_id" not in row or pd.isna(row["reach_id"]):
            row["reach_id"] = i
        else:
            row["reach_id"] = int(row["reach_id"])
    return records


def load_node_matrix_from_csv(path: Union[str, Path]) -> np.ndarray:
    import pandas as pd

    matrix = pd.read_csv(path, index_col=0).values.astype(float)
    if matrix.ndim != 2 or matrix.shape[0] != matrix.shape[1]:
        raise ValueError("node_matrix.csv 必须是方阵")
    return matrix


def compute_node_net_outflows(
    solver: RiverNetworkPreissmannSolver,
    reach_q_profiles: Sequence[Union[ReachState, np.ndarray]],
) -> np.ndarray:
    """按节点连续方程符号计算各节点净流出量。"""
    outflows = np.zeros(solver.n_nodes, dtype=float)
    for reach, q_item in zip(solver.reaches, reach_q_profiles):
        q = q_item.q if isinstance(q_item, ReachState) else np.asarray(q_item)
        s = solver.id_to_index[reach.data.start_node]
        e = solver.id_to_index[reach.data.end_node]
        outflows[s] += q[0]
        outflows[e] -= q[-1]
    return outflows


# 与 app.py 使用同一水情 API；程序启动时读取一次，后续计算中保持恒定
LIVE_BOUNDARY_API_URL = "https://waterlevel.gd.hydrosim.cn/api/scenario/latest"
BOUNDARY_CHANNEL_MAP = {
    1: ("taihu", "stage"),
    3: ("canal-south", "stage"),
    6: ("canal-north", "flow"),
}
DEFAULT_BOUNDARY_VALUES = {
    1: 13.2,
    3: 13.3,
    6: -5.0,
}


def fetch_constant_boundary_values(
    api_url: str = LIVE_BOUNDARY_API_URL,
    timeout: float = 5.0,
    use_fallback: bool = True,
) -> Dict[int, float]:
    """
    启动时从水情 API 读取节点 1、3、6 的实时边界值。
    返回值在 72 小时计算全程中作为恒定边界条件使用。
    """
    from datetime import datetime

    try:
        import requests
    except ImportError as exc:
        if not use_fallback:
            raise ImportError("获取实时边界需要安装 requests 库") from exc
        print("未安装 requests，使用默认边界条件。")
        return DEFAULT_BOUNDARY_VALUES.copy()

    print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] 正在获取节点 1/3/6 实时边界数据...")
    try:
        response = requests.get(api_url, timeout=timeout)
        if response.status_code != 200:
            raise RuntimeError(f"API 返回状态码 {response.status_code}")
        payload = response.json()
        channels = payload.get("channels", {})
        if not channels:
            raise RuntimeError("API 响应中缺少 channels 字段")

        values: Dict[int, float] = {}
        for node_id, (channel_key, bc_type) in BOUNDARY_CHANNEL_MAP.items():
            channel = channels.get(channel_key)
            if channel is None or "value" not in channel:
                raise RuntimeError(f"API 响应中缺少通道 {channel_key}")
            raw_value = float(channel["value"])
            if bc_type == "flow":
                # 与原先 flow_node6 符号约定一致：北侧入流取负值
                values[node_id] = -raw_value
            else:
                values[node_id] = raw_value

        print("实时边界读取成功（全程保持恒定）：")
        print(f"  节点1 水位 = {values[1]:.4f} m  (taihu)")
        print(f"  节点3 水位 = {values[3]:.4f} m  (canal-south)")
        print(f"  节点6 流量 = {values[6]:.4f} m3/s  (canal-north, 已按模型符号取负)")
        return values
    except Exception as exc:
        if not use_fallback:
            raise RuntimeError(f"获取实时边界失败: {exc}") from exc
        print(f"获取实时边界失败，改用默认值: {exc}")
        return DEFAULT_BOUNDARY_VALUES.copy()


def read_simulation_hours(default: float = 72.0) -> float:
    """读取用户输入的计算时长（小时）。"""
    while True:
        raw = input(f"请输入计算时长（小时，直接回车默认 {default:g}）：").strip()
        if not raw:
            return default
        try:
            hours = float(raw)
            if hours <= 0:
                raise ValueError("时长必须大于 0")
            return hours
        except ValueError:
            print("输入无效，请输入大于 0 的数字。")

# ============================================================
# 一个简单示例
# ============================================================

if __name__ == "__main__":
    import pandas as pd

    script_dir = Path(__file__).resolve().parent
    reach_table_raw = load_reach_table_from_csv(script_dir / "拓扑河段表.csv")
    node_matrix = load_node_matrix_from_csv(script_dir / "node_matrix.csv")

    # ---------------------------------------------------------
    # （二）河网求解方法及参数设定
    # ---------------------------------------------------------
    reach_table = reach_table_raw

    # ---------------------------------------------------------
    # （三）边界条件：启动时读取实时值，计算全程保持恒定
    # ---------------------------------------------------------
    live_boundary_values = fetch_constant_boundary_values()

    boundary_conditions = {
        1: {"type": "stage", "value": live_boundary_values[1]},
        3: {"type": "stage", "value": live_boundary_values[3]},
        6: {"type": "flow", "value": live_boundary_values[6]},
    }
    # 节点 7~20 为断头河，流量恒为 0
    for dead_end_node in range(7, 21):
        boundary_conditions[dead_end_node] = {"type": "flow", "value": 0.0}

    initial_node_heads = {
        1: live_boundary_values[1],
        3: live_boundary_values[3],
        6: live_boundary_values[3],
    }

    dt = 300.0  # 5分钟时步
    sim_hours = read_simulation_hours()
    n_steps = int(sim_hours * 3600.0 / dt)
    hours_label = str(int(sim_hours)) if sim_hours == int(sim_hours) else f"{sim_hours:g}"

    solver = RiverNetworkPreissmannSolver(
        reach_table=reach_table,
        node_matrix=node_matrix,
        boundary_conditions=boundary_conditions,
        dt=dt,
        theta=0.65,
        initial_node_heads=initial_node_heads,
        default_initial_depth=13.5,
        outer_tol=1.0e-5,
        outer_maxiter=20,
        outer_relaxation=0.8,
    )

    print(f"模型初始化完毕，开始计算 {hours_label} 小时演进...")
    result = solver.solve(n_steps=n_steps, save_full_reach_history=True)

    # ---------------------------------------------------------
    # （四）结果导出
    # ---------------------------------------------------------
    steps_per_hour = int(3600 / dt)
    if sim_hours == int(sim_hours):
        hour_list = list(range(int(sim_hours) + 1))
    else:
        hour_list = [round(h, 4) for h in np.linspace(0.0, sim_hours, int(sim_hours) + 1)]
    node_ids = result["node_ids"]

    def _step_index_for_hour(hour: float) -> int:
        return min(int(round(hour * steps_per_hour)), n_steps)

    print(f"\n正在生成 {hours_label} 小时全节点水位流量过程 Excel...")
    stage_rows = {"时间/小时": hour_list}
    flow_rows = {"时间/小时": hour_list}
    for nid in node_ids:
        node_idx = solver.id_to_index[nid]
        stage_rows[f"节点{nid}#水位(m)"] = [
            result["node_head_history"][_step_index_for_hour(hr), node_idx] for hr in hour_list
        ]
        flow_rows[f"节点{nid}#流量(m3/s)"] = [
            compute_node_net_outflows(
                solver, result["reach_history_q"][_step_index_for_hour(hr)]
            )[node_idx]
            for hr in hour_list
        ]

    node_process_file = script_dir / f"{hours_label}小时全节点水位流量过程.xlsx"
    with pd.ExcelWriter(node_process_file, engine="openpyxl") as writer:
        pd.DataFrame(stage_rows).to_excel(writer, sheet_name="节点水位", index=False)
        pd.DataFrame(flow_rows).to_excel(writer, sheet_name="节点流量", index=False)
    print(f"已保存: {node_process_file}")

    print(f"\n正在生成第 {hours_label} 小时河段水面线与流量分布 Excel...")
    step_final = _step_index_for_hour(sim_hours)
    profile_rows: List[Dict[str, Any]] = []
    for reach_idx, reach in enumerate(solver.reaches):
        q_profile = result["reach_history_q"][step_final][reach_idx]
        z_profile = result["reach_history_z"][step_final][reach_idx]
        x_profile = np.linspace(0.0, reach.data.length, reach.n_nodes)
        reach_meta = reach_table_raw[reach_idx]
        for sec_idx in range(reach.n_nodes):
            profile_rows.append(
                {
                    "reach_id": reach_meta.get("reach_id", reach_idx),
                    "start_node": reach.data.start_node,
                    "end_node": reach.data.end_node,
                    "section": sec_idx,
                    "x_m": round(float(x_profile[sec_idx]), 2),
                    "z_m": round(float(z_profile[sec_idx]), 4),
                    "q_m3s": round(float(q_profile[sec_idx]), 4),
                }
            )

    reach_profile_file = script_dir / f"{hours_label}小时河段水面线流量分布.xlsx"
    pd.DataFrame(profile_rows).to_excel(
        reach_profile_file, index=False, sheet_name=f"第{hours_label}小时"
    )
    print(f"已保存: {reach_profile_file}")