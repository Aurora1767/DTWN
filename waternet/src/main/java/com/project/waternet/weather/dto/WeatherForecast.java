package com.project.waternet.weather.dto;

import java.util.List;

public record WeatherForecast(
		List<DailyForecast> daily,
		List<MinutelyPrecip> minutely,
		String updatedAt) {

	public record DailyForecast(
			String fxDate,
			String textDay,
			String textNight,
			String tempMax,
			String tempMin,
			String windSpeedDay,
			String windScaleDay,
			String windDirDay,
			String precip,
			String humidity) {
	}

	public record MinutelyPrecip(
			String fxTime,
			String precip,
			String type) {
	}
}
