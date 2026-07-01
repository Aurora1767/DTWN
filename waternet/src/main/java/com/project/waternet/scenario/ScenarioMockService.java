package com.project.waternet.scenario;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.project.waternet.scenario.dto.DispatchPlan;

@Service
public class ScenarioMockService {

	public List<DispatchPlan> plans() {
		LocalDateTime now = LocalDateTime.now();
		return List.of(
				new DispatchPlan(
						"PLAN-FLOOD-01",
						"太湖水位突涨防洪预案",
						"防洪调度",
						"太湖边界水位超过 2.60 m，且京杭运河北站持续上涨",
						List.of("蠡河节制闸维持开启", "大溪港泵站提高外排流量", "重点巡查蠡河 S2 低洼岸段"),
						"降低蠡河 S2 最高水位约 0.06 m，缩短超警持续时间",
						"WARNING",
						List.of("Lihe_S1", "Lihe_S2", "Daxigang"),
						now.minusHours(3)),
				new DispatchPlan(
						"PLAN-WQ-01",
						"低流速弱交换改善预案",
						"水环境改善",
						"支河平均流速低于 0.15 m/s 且连续 6 小时无明显交换",
						List.of("开启大溪港泵站小流量循环", "联动蠡河闸形成短时补水", "监测弱交换河段溶解氧变化"),
						"提升重点河段平均流速，减少滞留区范围",
						"WATCH",
						List.of("Daxigang", "Lihe_S2"),
						now.minusDays(1)),
				new DispatchPlan(
						"PLAN-RAIN-01",
						"短历时强降雨预排预案",
						"预报预排",
						"未来 3 小时累计降雨超过 30 mm",
						List.of("提前降低关键节点控制水位", "预置泵站待命状态", "滚动更新模型边界条件"),
						"为突发降雨预留调蓄空间，降低节点超限概率",
						"WATCH",
						List.of("Jinghang_S2", "Daxigang"),
						now.minusDays(2)));
	}
}
