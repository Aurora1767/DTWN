package com.project.waternet.warning;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.waternet.common.ApiResponse;
import com.project.waternet.warning.dto.WarningEvent;

@RestController
@RequestMapping("/api/warnings")
public class WarningController {

	@GetMapping
	public ApiResponse<List<WarningEvent>> events() {
		LocalDateTime now = LocalDateTime.now();
		return ApiResponse.ok(List.of(
				new WarningEvent("W-20260701-001", "N_JH_N", "京杭运河北站", "waterLevel", 2.66, 2.60, "WARNING", "UNCONFIRMED", now.minusMinutes(12)),
				new WarningEvent("W-20260701-002", "Lihe_S2", "蠡河 S2", "velocity", 0.12, 0.15, "WATCH", "PROCESSING", now.minusMinutes(27))));
	}
}
