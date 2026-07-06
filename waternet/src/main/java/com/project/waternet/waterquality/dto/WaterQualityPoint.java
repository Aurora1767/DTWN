package com.project.waternet.waterquality.dto;

public record WaterQualityPoint(
		int nodeId,
		double ph,
		double dissolvedOxygen,
		double permanganateIndex,
		double ammoniaNitrogen,
		double totalPhosphorus,
		double chemicalOxygenDemand,
		double bod5,
		String level) {
}
