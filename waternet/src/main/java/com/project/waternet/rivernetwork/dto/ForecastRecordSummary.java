package com.project.waternet.rivernetwork.dto;

public record ForecastRecordSummary(
		long id,
		String calculatedAt,
		double forecastHours,
		String recordType,
		String simulationName) {
}
