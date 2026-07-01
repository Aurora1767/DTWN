package com.project.waternet.network.dto;

public record WaterNode(
		String code,
		String name,
		String type,
		double lng,
		double lat,
		double initialWaterLevel,
		String boundaryType) {
}
