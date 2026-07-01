package com.project.waternet.simulation.dto;

public record TimeSeriesPoint(
		int step,
		int timeSeconds,
		double waterLevel,
		double flow,
		double velocity,
		String riskLevel) {
}
