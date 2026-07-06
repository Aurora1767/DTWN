package com.project.waternet.waterquality.dto;

import java.util.List;

public record WaterQualityNodeHistory(
		int nodeId,
		List<WaterQualityHistoryPoint> points) {
}
