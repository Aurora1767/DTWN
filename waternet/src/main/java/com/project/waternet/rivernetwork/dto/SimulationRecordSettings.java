package com.project.waternet.rivernetwork.dto;

import java.util.List;
import java.util.Map;

public record SimulationRecordSettings(
		String simulationName,
		String simulationStartAt,
		double forecastHours,
		double dt,
		int nSteps,
		String status,
		Map<String, Double> boundaryValues,
		Map<String, List<Double>> boundarySeries,
		Map<String, Double> gateOpenings) {
}
