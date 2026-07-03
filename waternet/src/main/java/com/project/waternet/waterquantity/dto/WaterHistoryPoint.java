package com.project.waternet.waterquantity.dto;

public record WaterHistoryPoint(
		String date,
		double waterLevel,
		double flowRate,
		double rainfall) {
}
