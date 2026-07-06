package com.project.waternet.rivernetwork.dto;

import java.util.List;
import java.util.Map;

public record RiverNetworkForecastRequest(
		double forecastHours,
		String calculatedAt,
		String simulationStartAt,
		String simulationName,
		String recordType,
		Map<String, Double> boundaryValues,
		Map<String, List<Double>> boundarySeries,
		Map<String, Double> initialNodeHeads,
		Map<String, Double> gateOpenings,
		Double dt) {
}
