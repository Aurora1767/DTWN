package com.project.waternet.simulation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.project.waternet.simulation.dto.SimulationRequest;
import com.project.waternet.simulation.dto.SimulationResult;

@Service
public class SimulationService {

	private final ModelRunner modelRunner;
	private final List<SimulationResult> history = new ArrayList<>();

	public SimulationService(ModelRunner modelRunner) {
		this.modelRunner = modelRunner;
	}

	public SimulationResult run(SimulationRequest request) {
		SimulationResult result = modelRunner.run(request);
		history.add(result);
		return result;
	}

	public List<SimulationResult> history() {
		return history.stream()
				.sorted(Comparator.comparing(SimulationResult::startedAt).reversed())
				.toList();
	}

	public SimulationResult find(String runId) {
		return history.stream()
				.filter(result -> result.runId().equals(runId))
				.findFirst()
				.orElseGet(() -> modelRunner.run(new SimulationRequest("示例历史工况", "manual", 20, 2.5, 300, 48, List.of())));
	}
}
