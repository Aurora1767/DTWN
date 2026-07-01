package com.project.waternet.realtime.dto;

import java.time.LocalDateTime;

public record SensorSnapshot(
		String stationCode,
		String stationName,
		String nodeCode,
		double waterLevel,
		double flow,
		double velocity,
		double rainfall,
		String status,
		LocalDateTime observedAt) {
}
