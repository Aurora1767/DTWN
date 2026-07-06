package com.project.waternet.rivernetwork.dto;

import java.util.List;
import java.util.Map;

public record RiverNetworkForecastResult(
		String status,
		double forecastHours,
		int nSteps,
		double dt,
		double simulatedSeconds,
		String timestamp,
		Map<String, Double> nodeHeads,
		Map<String, Double> nodeFlows,
		Map<String, Double> boundaryValues,
		List<RiverNetworkNodeResult> nodes,
		List<RiverNetworkNodeHistory> nodeHistories,
		List<RiverNetworkReachResult> reaches,
		List<RiverNetworkReachProfile> reachProfiles,
		List<RiverNetworkReachHistory> reachHistories) {
}
