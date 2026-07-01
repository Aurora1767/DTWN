package com.project.waternet.warning.dto;

import java.time.LocalDateTime;

public record WarningEvent(
		String id,
		String targetCode,
		String targetName,
		String metric,
		double value,
		double threshold,
		String level,
		String status,
		LocalDateTime triggeredAt) {
}
