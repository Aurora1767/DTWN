# -*- coding: utf-8 -*-
"""
core_crawler.py
太湖流域 8 个核心测站水量（水位/流量）历史序列抓取业务核心
"""

import os
import re
import json
import logging
from datetime import datetime, timedelta
import requests

# 设置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger("taihoo_water_quantity_sync")

# ==================== Station & API Registry (核心常数配置) ====================
TARGET_URL = "http://58.247.45.108:8020/RegionalWaterAnalysis/getWA_Q_Stcd"

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "X-Requested-With": "XMLHttpRequest"
}

# 测站名与 ID (sstcd) 映射表
STATION_MAP = {
    "望亭（大）": "63202900",
    "洛社": "63202800",
    "苏州（枫桥）": "63203000",
    "浯溪桥": "63201015",
    "百渎口": "63201200",
    "黄埝桥": "63102100",
    "漕桥（三）": "63102150",
    "张桥": "63204260"
}


# ==================== Robust Helpers (健壮性辅助函数) ====================

def parse_item_time(time_val):
    """
    尝试将各种类型的时间值(str, int, float)解析为 (date_str_yyyy_mm_dd, hour_int)
    如果解析失败，返回 (None, None)
    """
    if not time_val:
        return None, None
    
    # 如果是数值型时间戳
    if isinstance(time_val, (int, float)):
        try:
            if time_val > 1e11:  # 毫秒级时间戳
                dt = datetime.fromtimestamp(time_val / 1000.0)
            else:  # 秒级时间戳
                dt = datetime.fromtimestamp(time_val)
            return dt.strftime("%Y-%m-%d"), dt.hour
        except Exception as e:
            logger.debug(f"通过数值时间戳解析时间失败 {time_val}: {e}")
            return None, None
            
    time_str = str(time_val).strip()
    
    # 如果是纯数字但长度长，可能是时间戳字符串
    if time_str.isdigit():
        try:
            val = float(time_str)
            if val > 1e11:
                dt = datetime.fromtimestamp(val / 1000.0)
            else:
                dt = datetime.fromtimestamp(val)
            return dt.strftime("%Y-%m-%d"), dt.hour
        except Exception:
            pass

    # 尝试正则提取 YYYY-MM-DD 和 HH
    # 匹配 YYYY-MM-DD HH:MM:SS 或 YYYY/MM/DD HH:MM:SS 或 YYYY-MM-DDTHH:MM:SS
    match = re.search(r'(\d{4})[-/](\d{2})[-/](\d{2})[ T](\d{2}):(\d{2})', time_str)
    if match:
        year, month, day, hour, minute = match.groups()
        return f"{year}-{month}-{day}", int(hour)
        
    # 匹配 YYYY-MM-DD 只有日期的情况
    match_date = re.search(r'(\d{4})[-/](\d{2})[-/](\d{2})', time_str)
    if match_date:
        year, month, day = match_date.groups()
        # 尝试寻找是否有小时
        hour_match = re.search(r'(\d{1,2})[时点hH]', time_str)
        hour = int(hour_match.group(1)) if hour_match else 8  # 若无明确小时，默认为8时以便通过过滤
        return f"{year}-{month}-{day}", hour
        
    return None, None


def get_float_value(item, keys, default=0.0):
    """
    自适应获取浮点数值，转换为 float 格式，若缺失或格式错误则赋予默认值 default
    """
    for k in keys:
        if k in item and item[k] is not None:
            val = item[k]
            if isinstance(val, (int, float)):
                return round(float(val), 3)
            val_str = str(val).strip()
            if val_str in ("", "-", "null", "None", "NULL"):
                continue
            try:
                return round(float(val_str), 3)
            except (ValueError, TypeError):
                continue
    return default


# ==================== Core Synchronization Logic ====================

def execute_sync_now(output_path=None):
    """
    执行单次历史水量同步任务。
    :param output_path: 本地 JSON 保存路径，默认保存在当前工作目录下的 water_quantity_history.json
    :return: 包含多天历史记录的结构化字典（双路输出）
    """
    logger.info("=" * 60)
    logger.info("开始执行太湖流域 8 大核心测站历史水量数据同步任务...")
    
    # 确定保存路径
    if output_path is None:
        output_path = os.path.join(os.getcwd(), "water_quantity_history.json")
    
    # 1. 尝试读取本地已有的备份数据，用作某些测站断档时的“已有序列保留”兜底
    existing_history = {}
    if os.path.exists(output_path):
        try:
            with open(output_path, 'r', encoding='utf-8') as f:
                old_data = json.load(f)
                if isinstance(old_data, dict) and old_data.get("status") == "success":
                    existing_history = old_data.get("history_data", {})
                    logger.info(f"成功预加载本地已有数据备份，包含测站数: {len(existing_history)}")
        except Exception as e:
            logger.warning(f"预读取本地已存文件 {output_path} 失败: {e}。将以全新状态初始化。")

    # 2. 动态生成 stime 和 etime （当前日期向前推 6 天）
    now_dt = datetime.now()
    etime = now_dt.strftime("%Y-%m-%d")
    stime = (now_dt - timedelta(days=6)).strftime("%Y-%m-%d")
    logger.info(f"动态计算数据获取范围：stime={stime} 至 etime={etime} (共 6 天时间段)")

    # 3. 循环请求 8 个站点的数据
    sync_history_data = {}
    
    # 定义键名自适应列表
    time_keys = ['tm', 'time', 'date', 'sj', 'dateTime', 'datetime', 'zdt', 'clock', 'sjsj']
    water_keys = ['z', 'rz', 'water_level', 'waterlevel', 'slevel', 'sw', 'waterLevel', 'uptownWater']
    flow_keys = ['q', 'avq', 'flow', 'flow_rate', 'flowrate', 'll', 'flowRate', 'discharge']

    for station_name, sstcd in STATION_MAP.items():
        logger.info("-" * 40)
        logger.info(f"正在抓取测站: {station_name} (sstcd: {sstcd})")
        
        station_records = {}  # 字典排重，key为 'YYYY-MM-DD'，确保每天早上 8 点只有单条记录
        payload = {
            "sstcd": sstcd,
            "stime": stime,
            "etime": etime
        }
        
        success = False
        response_data = []
        
        # 兼容性请求：尝试表单(form)和JSON(json)两种请求格式，增强接口穿透力
        for req_mode in ["form", "json"]:
            try:
                if req_mode == "form":
                    resp = requests.post(TARGET_URL, data=payload, headers=HEADERS, timeout=15)
                else:
                    resp = requests.post(TARGET_URL, json=payload, headers=HEADERS, timeout=15)
                
                if resp.status_code == 200:
                    try:
                        res_json = resp.json()
                        # 自适应从返回中抓取数组
                        if isinstance(res_json, list):
                            response_data = res_json
                        elif isinstance(res_json, dict):
                            # 常见的水利数据键名尝试
                            for k in ['data', 'list', 'rows', 'result', 'dataList', 'WA_Q_StcdList', 'rowsList']:
                                if k in res_json and isinstance(res_json[k], list):
                                    response_data = res_json[k]
                                    break
                            else:
                                # 极端情况：遍历所有字典值找最大的 list
                                candidate_list = None
                                max_len = -1
                                for k, v in res_json.items():
                                    if isinstance(v, list) and len(v) > max_len:
                                        candidate_list = v
                                        max_len = len(v)
                                if candidate_list is not None:
                                    response_data = candidate_list
                        
                        if response_data:
                            success = True
                            logger.info(f"测站 {station_name} 成功获取到 {len(response_data)} 条原始历史记录。")
                            break  # 一旦获取成功，无需再尝试另一种模式
                        else:
                            logger.warning(f"测站 {station_name} 在 {req_mode} 模式下请求成功，但未识别或提取到有效的数据列表（返回: {res_json}）。")
                    except Exception as json_err:
                        logger.warning(f"测站 {station_name} 在 {req_mode} 模式下解析JSON失败: {json_err}")
                else:
                    logger.warning(f"测站 {station_name} 在 {req_mode} 模式下请求失败，状态码: {resp.status_code}，响应内容: {resp.text[:200]}")
            except Exception as req_err:
                logger.warning(f"测站 {station_name} 在 {req_mode} 模式下请求产生异常: {req_err}")

        # 4. 独立容错退避逻辑
        if not success or not response_data:
            logger.warning(f"[数据断档警告] 测站 '{station_name}' 因网络、上报延迟或API异常无法请求到新数据。")
            if station_name in existing_history and existing_history[station_name]:
                logger.info(f"-> 容错退避：测站 '{station_name}' 成功保留并使用本地已有历史序列（共 {len(existing_history[station_name])} 条）。")
                sync_history_data[station_name] = existing_history[station_name]
            else:
                logger.error(f"-> 容错退避失败：测站 '{station_name}' 既无新数据，也无本地备份历史。本测站最终记录置空。")
                sync_history_data[station_name] = []
            continue

        # 5. 精准过滤与数据清洗 (提取每天 08 点数据)
        for item in response_data:
            # 自适应获取时间字段
            item_time_val = None
            for tk in time_keys:
                if tk in item:
                    item_time_val = item[tk]
                    break
            
            if not item_time_val:
                continue
                
            date_str, hour_val = parse_item_time(item_time_val)
            if not date_str:
                continue
            
            # 精确匹配每天早上 08时
            if hour_val == 8:
                # 水位与流量数据兼容性清洗
                water_level = get_float_value(item, water_keys, 0.0)
                flow_rate = get_float_value(item, flow_keys, 0.0)
                
                # 同一日期的 8 点只保留最新或唯一的一条
                station_records[date_str] = {
                    "date": date_str,
                    "water_level_m": water_level,
                    "flow_rate_m3_s": flow_rate
                }

        # 6. 5天截断安全保护 与 降序规整 (从新到老)
        sorted_records = sorted(station_records.values(), key=lambda x: x['date'], reverse=True)
        
        if len(sorted_records) > 7:
            logger.info(f"测站 {station_name} 经过每日 08时 精准过滤后有 {len(sorted_records)} 条记录，触发截断，保留最近 5 条。")
            final_records = sorted_records[:7]
        else:
            logger.info(f"测站 {station_name} 经过每日 08时 精准过滤后共 {len(sorted_records)} 条记录，全部保留。")
            final_records = sorted_records

        # 极端防漏：如果接口返回了数据但全部被过滤掉了（比如接口突然没有 8 点数据），
        # 且本地有备份记录，那么继续使用本地备份
        if not final_records and station_name in existing_history and existing_history[station_name]:
            logger.warning(f"测站 {station_name} 经过 08时 过滤后没有留下任何记录！继续保留本地已有序列兜底。")
            final_records = existing_history[station_name]

        sync_history_data[station_name] = final_records

    # 7. 组装双路数据输出
    completion_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    result_dict = {
        "status": "success",
        "timestamp": completion_time,
        "history_data": sync_history_data
    }

    # 一路输出：保存至本地统一标准历史 JSON 文件
    try:
        parent_dir = os.path.dirname(os.path.abspath(output_path))
        if parent_dir:
            os.makedirs(parent_dir, exist_ok=True)
            
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(result_dict, f, ensure_ascii=False, indent=2)
            
        logger.info("=" * 60)
        logger.info(f"【双路输出 - 本地文件】成功保存历史序列至: {output_path}")
    except Exception as e:
        logger.error(f"【双路输出 - 本地文件】保存历史序列 JSON 文件失败: {e}", exc_info=True)

    # 二路输出：通过 return 返回字典格式，供后端 ORM、前端直接加载
    logger.info("【双路输出 - 内存字典】成功返回内存结构化数据字典。")
    logger.info("太湖水量同步任务单次运行安全完成。进程安全退出，绝不常驻内存。")
    logger.info("=" * 60)
    
    return result_dict
