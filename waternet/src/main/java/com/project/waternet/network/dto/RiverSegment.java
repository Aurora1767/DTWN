package com.project.waternet.network.dto;

import java.util.List;

public record RiverSegment(
		String code,
		String name,
		double lengthMeters,
		double widthMeters,
		double manningN,
		String startNodeCode,
		String endNodeCode,
		List<Coordinate> coordinates) {

	public record Coordinate(double lng, double lat) {
	}
}
