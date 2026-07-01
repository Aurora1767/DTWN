package com.project.waternet.history.dto;

import java.time.LocalDateTime;

public record HistoricalSensorRecord(
		String stationCode,
		String stationName,
		String nodeCode,
		LocalDateTime observedAt,
		double waterLevel,
		double flow,
		double velocity,
		double rainfall,
		String status) {
}
