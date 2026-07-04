package com.project.waternet.network.dto;

import java.util.List;

public record SegmentProfile(
		String segmentCode,
		Integer reachId,
		String startNodeCode,
		String endNodeCode,
		Integer profileHour,
		List<SegmentProfilePoint> points) {

	public record SegmentProfilePoint(
			int sectionNo,
			double distanceMeters,
			Double waterLevel,
			Double flow) {
	}
}
