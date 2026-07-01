package com.project.waternet.scenario;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.waternet.common.ApiResponse;
import com.project.waternet.scenario.dto.DispatchPlan;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {

	private final ScenarioMockService scenarioMockService;

	public ScenarioController(ScenarioMockService scenarioMockService) {
		this.scenarioMockService = scenarioMockService;
	}

	@GetMapping("/plans")
	public ApiResponse<List<DispatchPlan>> plans() {
		return ApiResponse.ok(scenarioMockService.plans());
	}
}
