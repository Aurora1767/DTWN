package com.project.waternet.waterquality.dto;

import java.util.List;
import java.util.Map;

public record WaterQualityOverview(
		String status,
		String recordTime,
		boolean live,
		Map<String, Integer> summary,
		List<WaterQualityPoint> nodes) {
}
