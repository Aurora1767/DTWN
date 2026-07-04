package com.project.waternet.network.dto;

import java.util.List;

public record WaterNode(
		String code,
		String name,
		String type,
		double lng,
		double lat,
		double initialWaterLevel,
		String boundaryType,
		List<String> connectedNodeCodes,
		List<String> connectedSegmentCodes,
		List<Integer> connectedReachIds,
		NodeLatestHydrology latestHydrology) {

	public WaterNode(String code, String name, String type, double lng, double lat, double initialWaterLevel,
			String boundaryType) {
		this(code, name, type, lng, lat, initialWaterLevel, boundaryType, List.of(), List.of(), List.of(), null);
	}

	public record NodeLatestHydrology(
			Integer hour,
			Double waterLevel,
			Double flow) {
	}
}
