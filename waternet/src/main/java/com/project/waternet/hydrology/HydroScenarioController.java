package com.project.waternet.hydrology;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.waternet.common.ApiResponse;
import com.project.waternet.hydrology.dto.HydroBoundarySnapshot;
import com.project.waternet.hydrology.dto.HydroScenarioRecord;
import com.project.waternet.hydrology.dto.HydroScenarioSnapshot;

@RestController
@RequestMapping("/api/hydro-scenario")
public class HydroScenarioController {

	private final HydroScenarioService hydroScenarioService;

	public HydroScenarioController(HydroScenarioService hydroScenarioService) {
		this.hydroScenarioService = hydroScenarioService;
	}

	@GetMapping("/latest")
	public ApiResponse<HydroScenarioSnapshot> latest() {
		return ApiResponse.ok(hydroScenarioService.fetchLatest());
	}

	@GetMapping("/merged-list")
	public ApiResponse<List<HydroScenarioRecord>> mergedList(
			@RequestParam(value = "start_date", required = false) String startDate,
			@RequestParam(value = "end_date", required = false) String endDate,
			@RequestParam(value = "page_size", defaultValue = "100") int pageSize) {
		return ApiResponse.ok(hydroScenarioService.fetchMergedRecords(startDate, endDate, pageSize));
	}

	@GetMapping("/boundary-at")
	public ApiResponse<HydroBoundarySnapshot> boundaryAt(@RequestParam("at") String at) {
		return ApiResponse.ok(hydroScenarioService.resolveBoundaryAt(at));
	}
}
