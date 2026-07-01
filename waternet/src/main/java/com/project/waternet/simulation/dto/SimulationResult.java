package com.project.waternet.simulation.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SimulationResult(
		String runId,
		String scenarioName,
		String status,
		String runnerType,
		LocalDateTime startedAt,
		LocalDateTime finishedAt,
		List<SegmentResult> results) {
}
