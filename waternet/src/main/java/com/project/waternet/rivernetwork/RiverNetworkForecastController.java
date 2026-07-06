package com.project.waternet.rivernetwork;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.waternet.common.ApiResponse;
import com.project.waternet.hydrology.HydroScenarioService;
import com.project.waternet.hydrology.dto.HydroBoundarySnapshot;
import com.project.waternet.rivernetwork.dto.ForecastRecordDetail;
import com.project.waternet.rivernetwork.dto.ForecastRecordSummary;
import com.project.waternet.rivernetwork.dto.RiverNetworkForecastRequest;
import com.project.waternet.rivernetwork.dto.RiverNetworkForecastResult;
import com.project.waternet.rivernetwork.dto.SimulationRecordSettings;

@RestController
@RequestMapping("/api/river-network")
public class RiverNetworkForecastController {

	private final RiverNetworkBridgeService riverNetworkBridgeService;
	private final ForecastRecordService forecastRecordService;
	private final HydroScenarioService hydroScenarioService;

	public RiverNetworkForecastController(
			RiverNetworkBridgeService riverNetworkBridgeService,
			ForecastRecordService forecastRecordService,
			HydroScenarioService hydroScenarioService) {
		this.riverNetworkBridgeService = riverNetworkBridgeService;
		this.forecastRecordService = forecastRecordService;
		this.hydroScenarioService = hydroScenarioService;
	}

	@PostMapping("/forecast")
	public ApiResponse<ForecastRecordDetail> forecast(@RequestBody(required = false) RiverNetworkForecastRequest request) {
		double forecastHours = request == null ? 1.0 : request.forecastHours();
		boolean isScenarioRun = request != null && isScenarioRunRequest(request);
		RiverNetworkForecastResult result;
		String scenarioName = null;
		Map<String, Double> boundaryValues = null;
		if (isScenarioRun) {
			scenarioName = resolveScenarioRunName(request);
			boundaryValues = resolveBoundaryValues(request);
			result = riverNetworkBridgeService.runSimulation(
					forecastHours,
					boundaryValues,
					request.boundarySeries(),
					request.initialNodeHeads(),
					request.simulationStartAt(),
					resolveDt(request),
					request.gateOpenings());
			if (!"success".equalsIgnoreCase(result.status())) {
				throw new IllegalStateException("河网情景演算模型未返回成功结果，请检查参数与 Python 环境");
			}
		} else {
			result = riverNetworkBridgeService.runForecast(forecastHours);
		}
		String calculatedAt = isScenarioRun
				? request.simulationStartAt()
				: request != null && request.calculatedAt() != null && !request.calculatedAt().isBlank()
						? request.calculatedAt()
						: Instant.now().toString();
		String recordType = resolveRecordType(request, isScenarioRun);
		SimulationRecordSettings settings = isScenarioRun
				? buildSimulationSettings(request, scenarioName, forecastHours, boundaryValues, result)
				: null;
		return ApiResponse.ok(forecastRecordService.save(calculatedAt, forecastHours, result, recordType, scenarioName, settings));
	}

	private SimulationRecordSettings buildSimulationSettings(
			RiverNetworkForecastRequest request,
			String simulationName,
			double forecastHours,
			Map<String, Double> boundaryValues,
			RiverNetworkForecastResult result) {
		return new SimulationRecordSettings(
				simulationName,
				request.simulationStartAt(),
				forecastHours,
				resolveDt(request),
				result.nSteps(),
				result.status(),
				boundaryValues,
				request.boundarySeries(),
				request.gateOpenings());
	}

	private String resolveScenarioRunName(RiverNetworkForecastRequest request) {
		if (request == null || request.simulationName() == null || request.simulationName().isBlank()) {
			if ("plan".equalsIgnoreCase(request == null ? null : request.recordType())) {
				throw new IllegalArgumentException("预案名称不能为空");
			}
			throw new IllegalArgumentException("预演名称不能为空");
		}
		return request.simulationName().trim();
	}

	private boolean isScenarioRunRequest(RiverNetworkForecastRequest request) {
		if ("simulation".equalsIgnoreCase(request.recordType()) || "plan".equalsIgnoreCase(request.recordType())) {
			return true;
		}
		if ("forecast".equalsIgnoreCase(request.recordType())) {
			return false;
		}
		return request.simulationStartAt() != null && !request.simulationStartAt().isBlank();
	}

	private String resolveRecordType(RiverNetworkForecastRequest request, boolean isScenarioRun) {
		if (!isScenarioRun) {
			return "forecast";
		}
		if (request != null && "plan".equalsIgnoreCase(request.recordType())) {
			return "plan";
		}
		return "simulation";
	}

	private Map<String, Double> resolveBoundaryValues(RiverNetworkForecastRequest request) {
		if (request.boundaryValues() != null && !request.boundaryValues().isEmpty()) {
			return request.boundaryValues();
		}
		HydroBoundarySnapshot boundary = hydroScenarioService.resolveBoundaryAt(request.simulationStartAt());
		return boundary.boundaryValues();
	}

	@GetMapping("/forecast-records")
	public ApiResponse<List<ForecastRecordSummary>> listForecastRecords(
			@RequestParam(defaultValue = "forecast") String type) {
		String recordType;
		if ("simulation".equalsIgnoreCase(type)) {
			recordType = "simulation";
		} else if ("plan".equalsIgnoreCase(type)) {
			recordType = "plan";
		} else {
			recordType = "forecast";
		}
		return ApiResponse.ok(forecastRecordService.listSummaries(recordType));
	}

	@GetMapping("/forecast-records/{id}")
	public ApiResponse<ForecastRecordDetail> getForecastRecord(@PathVariable long id) {
		return ApiResponse.ok(forecastRecordService.getDetail(id));
	}

	@DeleteMapping("/forecast-records/{id}")
	public ApiResponse<Void> deleteForecastRecord(@PathVariable long id) {
		forecastRecordService.delete(id);
		return ApiResponse.ok(null);
	}

	private double resolveDt(RiverNetworkForecastRequest request) {
		if (request == null || request.dt() == null || request.dt() <= 0) {
			return 300.0;
		}
		return request.dt();
	}
}
