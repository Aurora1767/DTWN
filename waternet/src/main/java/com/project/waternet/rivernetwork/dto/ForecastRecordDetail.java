package com.project.waternet.rivernetwork.dto;

public record ForecastRecordDetail(
		long id,
		String calculatedAt,
		double forecastHours,
		String recordType,
		String simulationName,
		SimulationRecordSettings settings,
		RiverNetworkForecastResult result) {
}
