package com.project.waternet.hydrology.dto;

import java.util.Map;

public record HydroBoundarySnapshot(
		String timestamp,
		Map<String, Double> boundaryValues) {
}
