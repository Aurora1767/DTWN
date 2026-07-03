package com.project.waternet.waterquantity.dto;

public record WaterStationSnapshot(
		String stationCode,
		String stationName,
		double waterLevel,
		double flowRate,
		String observedAt) {
}
