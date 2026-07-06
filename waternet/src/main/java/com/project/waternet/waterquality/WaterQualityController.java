package com.project.waternet.waterquality;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.waternet.common.ApiResponse;
import com.project.waternet.waterquality.dto.WaterQualityNodeHistory;
import com.project.waternet.waterquality.dto.WaterQualityOverview;

@RestController
@RequestMapping({ "/api/water-quality", "/api/water" })
public class WaterQualityController {

	private final WaterQualityService waterQualityService;

	public WaterQualityController(WaterQualityService waterQualityService) {
		this.waterQualityService = waterQualityService;
	}

	@GetMapping("/latest")
	public ApiResponse<WaterQualityOverview> latest() {
		return ApiResponse.ok(waterQualityService.latest());
	}

	@GetMapping("/nodes/{nodeId}/history")
	public ApiResponse<WaterQualityNodeHistory> nodeHistory(
			@PathVariable int nodeId,
			@RequestParam(defaultValue = "24") int hours) {
		return ApiResponse.ok(waterQualityService.nodeHistory(nodeId, hours));
	}
}
