package com.project.waternet.simulation;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.waternet.common.ApiResponse;
import com.project.waternet.simulation.dto.SimulationRequest;
import com.project.waternet.simulation.dto.SimulationResult;

@RestController
@RequestMapping("/api/simulations")
public class SimulationController {

	private final SimulationService simulationService;

	public SimulationController(SimulationService simulationService) {
		this.simulationService = simulationService;
	}

	@PostMapping("/run")
	public ApiResponse<SimulationResult> run(@RequestBody SimulationRequest request) {
		return ApiResponse.ok(simulationService.run(request));
	}

	@GetMapping
	public ApiResponse<List<SimulationResult>> history() {
		return ApiResponse.ok(simulationService.history());
	}

	@GetMapping("/{runId}")
	public ApiResponse<SimulationResult> detail(@PathVariable String runId) {
		return ApiResponse.ok(simulationService.find(runId));
	}
}
