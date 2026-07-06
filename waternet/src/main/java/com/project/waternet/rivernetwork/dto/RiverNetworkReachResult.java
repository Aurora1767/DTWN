package com.project.waternet.rivernetwork.dto;

public record RiverNetworkReachResult(
		int reachId,
		int startNode,
		int endNode,
		double length,
		double width,
		double inletFlow,
		double outletFlow,
		double avgWaterLevel,
		double maxWaterLevel,
		double minWaterLevel,
		double avgFlow) {
}
