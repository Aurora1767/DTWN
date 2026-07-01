package com.project.waternet.network.dto;

public record HydraulicStructure(
		String code,
		String name,
		String type,
		String nodeCode,
		double designFlow,
		String status,
		double lng,
		double lat) {
}
