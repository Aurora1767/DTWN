package com.project.waternet.hydrology.dto;

import java.util.Map;

public record HydroScenarioSnapshot(
		Map<String, HydroChannelValue> channels) {
}
