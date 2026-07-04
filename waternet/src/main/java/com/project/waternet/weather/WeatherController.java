package com.project.waternet.weather;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.waternet.common.ApiResponse;
import com.project.waternet.weather.dto.EnvironmentSnapshot;
import com.project.waternet.weather.dto.WeatherForecast;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {

	private final QWeatherBridgeService qWeatherBridgeService;

	public WeatherController(QWeatherBridgeService qWeatherBridgeService) {
		this.qWeatherBridgeService = qWeatherBridgeService;
	}

	@GetMapping("/environment")
	public ApiResponse<EnvironmentSnapshot> environment() {
		return ApiResponse.ok(qWeatherBridgeService.fetchEnvironmentSnapshot());
	}

	@GetMapping("/forecast")
	public ApiResponse<WeatherForecast> forecast() {
		return ApiResponse.ok(qWeatherBridgeService.fetchWeatherForecast());
	}
}
