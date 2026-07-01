package com.project.waternet.simulation.dto;

import java.util.List;

public record SimulationRequest(
		String scenarioName,
		String boundaryType,
		double upstreamFlow,
		double downstreamLevel,
		int timeStep,
		int steps,
		List<SegmentParameter> segments) {
}
