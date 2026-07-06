package com.project.waternet.weather.dto;

import java.util.List;

public record RainfallOverview(
		String status,
		String timestamp,
		boolean live,
		List<RainfallHistoryPoint> points) {
}
