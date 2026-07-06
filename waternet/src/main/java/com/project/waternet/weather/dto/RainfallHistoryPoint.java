package com.project.waternet.weather.dto;

public record RainfallHistoryPoint(
		String time,
		double upstream,
		double downstream,
		double rainfall) {
}
