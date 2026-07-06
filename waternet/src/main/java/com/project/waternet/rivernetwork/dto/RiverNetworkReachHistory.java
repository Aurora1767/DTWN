package com.project.waternet.rivernetwork.dto;

import java.util.List;

public record RiverNetworkReachHistory(
		int reachId,
		int startNode,
		int endNode,
		String label,
		List<Double> inletFlows,
		List<Double> outletFlows,
		List<Double> inletWaterLevels,
		List<Double> outletWaterLevels) {
}
