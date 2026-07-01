package com.project.waternet.simulation.dto;

import java.util.List;

public record SegmentResult(
		String segmentCode,
		String segmentName,
		double maxWaterLevel,
		double maxFlow,
		double averageVelocity,
		List<TimeSeriesPoint> series) {
}
