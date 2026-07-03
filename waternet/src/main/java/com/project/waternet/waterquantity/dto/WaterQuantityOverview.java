package com.project.waternet.waterquantity.dto;

import java.util.List;
import java.util.Map;

public record WaterQuantityOverview(
		String status,
		String timestamp,
		boolean live,
		List<WaterStationSnapshot> stations,
		Map<String, List<WaterHistoryPoint>> historyByCode) {
}
