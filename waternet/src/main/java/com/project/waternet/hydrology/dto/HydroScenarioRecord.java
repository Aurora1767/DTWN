package com.project.waternet.hydrology.dto;

public record HydroScenarioRecord(
		String timestamp,
		Double taihuValue,
		Double canalNorthValue,
		Double canalSouthValue) {
}
