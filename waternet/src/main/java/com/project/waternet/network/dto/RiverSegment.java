package com.project.waternet.network.dto;

import java.util.List;

public record RiverSegment(
		String code,
		String name,
		Integer reachId,
		double lengthMeters,
		double widthMeters,
		double manningN,
		Double chezy,
		Double dx,
		Double bedElevation,
		String startNodeCode,
		String endNodeCode,
		List<Coordinate> coordinates,
		SegmentHydrologyStats hydrologyStats) {

	public RiverSegment(String code, String name, double lengthMeters, double widthMeters, double manningN,
			String startNodeCode, String endNodeCode, List<Coordinate> coordinates) {
		this(code, name, null, lengthMeters, widthMeters, manningN, null, null, null, startNodeCode, endNodeCode,
				coordinates, null);
	}

	public record Coordinate(double lng, double lat) {
	}

	public record SegmentHydrologyStats(
			Double maxFlow,
			Double minFlow,
			Double maxWaterLevel,
			Double minWaterLevel,
			Integer profileHour,
			Integer sampleCount) {
	}
}
