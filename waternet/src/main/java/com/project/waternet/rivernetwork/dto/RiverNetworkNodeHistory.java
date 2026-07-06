package com.project.waternet.rivernetwork.dto;

import java.util.List;

public record RiverNetworkNodeHistory(
		int nodeId,
		List<Double> waterLevels,
		List<Double> netFlows) {
}
