package com.project.waternet.hydrology;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.waternet.common.ApiResponse;
import com.project.waternet.hydrology.dto.HydroScenarioSnapshot;

@RestController
@RequestMapping("/api/hydro-scenario")
public class HydroScenarioController {

	private final HydroScenarioService hydroScenarioService;

	public HydroScenarioController(HydroScenarioService hydroScenarioService) {
		this.hydroScenarioService = hydroScenarioService;
	}

	@GetMapping("/latest")
	public ApiResponse<HydroScenarioSnapshot> latest() {
		return ApiResponse.ok(hydroScenarioService.fetchLatest());
	}
}
