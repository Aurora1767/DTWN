package com.project.waternet.waterquantity;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.waternet.common.ApiResponse;
import com.project.waternet.waterquantity.dto.WaterHistoryPoint;
import com.project.waternet.waterquantity.dto.WaterQuantityOverview;
import com.project.waternet.waterquantity.dto.WaterStationSnapshot;

@RestController
@RequestMapping("/api/water-quantity")
public class WaterQuantityController {

	private final WaterQuantityBridgeService waterQuantityBridgeService;

	public WaterQuantityController(WaterQuantityBridgeService waterQuantityBridgeService) {
		this.waterQuantityBridgeService = waterQuantityBridgeService;
	}

	@GetMapping("/overview")
	public ApiResponse<WaterQuantityOverview> overview() {
		return ApiResponse.ok(waterQuantityBridgeService.fetchOverview());
	}

	@GetMapping("/stations")
	public ApiResponse<List<WaterStationSnapshot>> stations() {
		return ApiResponse.ok(waterQuantityBridgeService.fetchStations());
	}

	@GetMapping("/stations/{stationCode}/history")
	public ApiResponse<List<WaterHistoryPoint>> history(@PathVariable String stationCode) {
		return ApiResponse.ok(waterQuantityBridgeService.fetchStationHistory(stationCode));
	}
}
