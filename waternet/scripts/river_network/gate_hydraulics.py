# -*- coding: utf-8 -*-
"""Flat-gate discharge rating for simulation nodes."""

from __future__ import annotations

import math
from typing import Dict, Iterable, Mapping

G = 9.81

# node_id -> gate opening widths (m)
GATE_DEFINITIONS: Dict[int, list[float]] = {
    21: [22.00],
    28: [13.6, 40.46],
    29: [20.64],
    32: [22.35],
    34: [30.36],
    40: [25.22],
    47: [23.22],
}


def calc_gate_discharge(
    water_depth: float,
    opening_ratio: float,
    widths: Iterable[float],
    g: float = G,
) -> float:
    """
    Q = mu * b * e * sqrt(2 g H_u)
    mu = 0.60 - 0.176 * (e / H_u)

    opening_ratio: 0~1, relative gate opening (100% -> 1.0)
    e = opening_ratio * H_u
    """
    if water_depth <= 1.0e-6 or opening_ratio <= 0.0:
        return 0.0

    e = opening_ratio * water_depth
    ratio_e_h = e / water_depth
    mu = 0.60 - 0.176 * ratio_e_h
    mu = max(0.1, min(0.6, mu))
    sqrt_term = math.sqrt(2.0 * g * water_depth)

    total = 0.0
    for width in widths:
        total += mu * float(width) * e * sqrt_term
    return total


def build_gate_nodes_config(openings_percent: Mapping[int, float] | None) -> Dict[int, dict]:
    openings_percent = openings_percent or {}
    config: Dict[int, dict] = {}
    for node_id, widths in GATE_DEFINITIONS.items():
        pct = float(openings_percent.get(node_id, 100.0))
        pct = max(0.0, min(100.0, pct))
        config[node_id] = {
            "opening_ratio": pct / 100.0,
            "widths": list(widths),
        }
    return config
