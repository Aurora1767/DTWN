package com.project.waternet.scenario.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DispatchPlan(
		String code,
		String name,
		String type,
		String triggerCondition,
		List<String> measures,
		String expectedEffect,
		String riskLevel,
		List<String> relatedSegments,
		LocalDateTime updatedAt) {
}
