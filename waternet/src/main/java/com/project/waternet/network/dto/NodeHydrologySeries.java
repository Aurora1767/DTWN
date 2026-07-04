package com.project.waternet.network.dto;

import java.util.List;

public record NodeHydrologySeries(
		String nodeCode,
		List<NodeHydrologyPoint> points) {

	public record NodeHydrologyPoint(
			int hour,
			Double waterLevel,
			Double flow) {
	}
}
