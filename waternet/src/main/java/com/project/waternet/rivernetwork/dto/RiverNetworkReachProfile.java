package com.project.waternet.rivernetwork.dto;

import java.util.List;

public record RiverNetworkReachProfile(
		int reachId,
		int startNode,
		int endNode,
		String label,
		double length,
		List<Double> distances,
		List<Double> waterLevels,
		List<Double> flows) {
}
