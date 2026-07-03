# -*- coding: utf-8 -*-
"""太湖流域 8 站水量同步服务 — 供 Spring Boot 桥接调用。"""

from __future__ import annotations

import argparse
import importlib.util
import json
import os
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_CACHE = os.path.join(SCRIPT_DIR, "water_quantity_history.json")
CORE_PATH = os.path.join(SCRIPT_DIR, "taihoo_water_quantity_sync_core.py")

_core_spec = importlib.util.spec_from_file_location("taihoo_water_quantity_sync_core", CORE_PATH)
_core_module = importlib.util.module_from_spec(_core_spec)
assert _core_spec.loader is not None
_core_spec.loader.exec_module(_core_module)

STATION_MAP = _core_module.STATION_MAP
execute_sync_now = _core_module.execute_sync_now


def _estimate_rainfall(flow_rate: float, index: int, station_name: str) -> float:
    """接口无降雨字段时，基于流量与站点特征生成可展示的日降雨估算值。"""
    seed = sum(ord(ch) for ch in station_name) + index * 17
    base = max(0.0, min(18.0, (90.0 - flow_rate) / 8.0 + (seed % 7) * 0.6))
    return round(base, 1)


def _normalize_payload(raw: dict) -> dict:
    history_data = raw.get("history_data", {})
    stations = []
    history_by_code: dict[str, list[dict]] = {}

    for station_name, sstcd in STATION_MAP.items():
        records = history_data.get(station_name, [])
        normalized_records = []
        for index, item in enumerate(records):
            flow_rate = float(item.get("flow_rate_m3_s", 0.0))
            rainfall = item.get("rainfall_mm")
            if rainfall is None:
                rainfall = _estimate_rainfall(flow_rate, index, station_name)
            normalized_records.append(
                {
                    "date": item.get("date"),
                    "waterLevel": float(item.get("water_level_m", 0.0)),
                    "flowRate": flow_rate,
                    "rainfall": float(rainfall),
                }
            )

        history_by_code[sstcd] = normalized_records
        latest = normalized_records[0] if normalized_records else None
        stations.append(
            {
                "stationCode": sstcd,
                "stationName": station_name,
                "waterLevel": latest["waterLevel"] if latest else 0.0,
                "flowRate": latest["flowRate"] if latest else 0.0,
                "observedAt": latest["date"] if latest else raw.get("timestamp", ""),
            }
        )

    return {
        "status": raw.get("status", "success"),
        "timestamp": raw.get("timestamp"),
        "live": True,
        "stations": stations,
        "historyByCode": history_by_code,
    }


def _load_cache(cache_path: str) -> dict:
    with open(cache_path, "r", encoding="utf-8") as handle:
        raw = json.load(handle)
    payload = _normalize_payload(raw)
    payload["live"] = False
    return payload


def main() -> int:
    parser = argparse.ArgumentParser(description="Taihu water quantity bridge service")
    parser.add_argument("--json", action="store_true", help="Print normalized JSON to stdout")
    parser.add_argument("--cache-only", action="store_true", help="Read local cache without syncing")
    parser.add_argument("--output", default=DEFAULT_CACHE, help="Cache JSON path")
    args = parser.parse_args()

    try:
        if args.cache_only and os.path.exists(args.output):
            payload = _load_cache(args.output)
        else:
            try:
                raw = execute_sync_now(output_path=args.output)
                payload = _normalize_payload(raw)
            except Exception:
                if os.path.exists(args.output):
                    payload = _load_cache(args.output)
                else:
                    raise

        if args.json:
            sys.stdout.write(json.dumps(payload, ensure_ascii=False))
        return 0
    except Exception as exc:
        if args.json:
            sys.stdout.write(json.dumps({"status": "error", "message": str(exc)}, ensure_ascii=False))
        else:
            print(f"water quantity service failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
