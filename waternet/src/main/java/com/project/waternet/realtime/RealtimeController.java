package com.project.waternet.realtime;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.waternet.common.ApiResponse;
import com.project.waternet.realtime.dto.SensorSnapshot;

@RestController
@RequestMapping("/api/realtime")
public class RealtimeController {

	@GetMapping("/stations")
	public ApiResponse<List<SensorSnapshot>> stations() {
		LocalDateTime now = LocalDateTime.now();
		return ApiResponse.ok(List.of(
				new SensorSnapshot("ST_TH", "太湖边界站", "N_LH_TH", 2.52, 18.6, 0.31, 2.4, "NORMAL", now),
				new SensorSnapshot("ST_JH_N", "京杭运河北站", "N_JH_N", 2.55, 20.0, 0.28, 2.1, "WATCH", now),
				new SensorSnapshot("ST_JH_S", "京杭运河南站", "N_JH_S", 2.46, 17.4, 0.24, 1.8, "NORMAL", now),
				new SensorSnapshot("ST_DX", "大溪港站", "N_DX_W", 2.43, 14.2, 0.20, 2.6, "NORMAL", now)));
	}

	@GetMapping("/summary")
	public ApiResponse<Map<String, Object>> summary() {
		return ApiResponse.ok(Map.of(
				"waterLevelAverage", 2.49,
				"flowTotal", 70.2,
				"rainfall24h", 18.5,
				"onlineStations", 4,
				"warningCount", 2,
				"modelSyncStatus", "SYNCED"));
	}
}
