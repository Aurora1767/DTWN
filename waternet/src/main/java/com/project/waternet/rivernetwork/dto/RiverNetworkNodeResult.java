package com.project.waternet.rivernetwork.dto;

public record RiverNetworkNodeResult(
		int nodeId,
		double waterLevel,
		double netFlow) {
}
