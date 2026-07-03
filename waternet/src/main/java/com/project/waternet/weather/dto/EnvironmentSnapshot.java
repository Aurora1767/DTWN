package com.project.waternet.weather.dto;

public record EnvironmentSnapshot(
		String weatherText,
		String temperature,
		String windSpeed,
		String windScale,
		String observedAt) {
}
