package com.project.waternet.weather;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.waternet.common.ApiResponse;
import com.project.waternet.weather.dto.RainfallOverview;

@RestController
@RequestMapping("/api/rainfall")
public class RainfallController {

	private final RainfallService rainfallService;

	public RainfallController(RainfallService rainfallService) {
		this.rainfallService = rainfallService;
	}

	@GetMapping("/history-24h")
	public ApiResponse<RainfallOverview> history24h() {
		return ApiResponse.ok(rainfallService.fetchHistory24h());
	}
}
