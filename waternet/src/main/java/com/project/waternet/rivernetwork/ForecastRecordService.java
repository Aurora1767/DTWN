package com.project.waternet.rivernetwork;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.waternet.rivernetwork.dto.ForecastRecordDetail;
import com.project.waternet.rivernetwork.dto.ForecastRecordSummary;
import com.project.waternet.rivernetwork.dto.RiverNetworkForecastResult;
import com.project.waternet.rivernetwork.dto.RiverNetworkReachHistory;
import com.project.waternet.rivernetwork.dto.SimulationRecordSettings;

@Service
public class ForecastRecordService {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final ForecastRecordRepository forecastRecordRepository;

	public ForecastRecordService(ForecastRecordRepository forecastRecordRepository) {
		this.forecastRecordRepository = forecastRecordRepository;
	}

	public ForecastRecordDetail save(
			String calculatedAt,
			double forecastHours,
			RiverNetworkForecastResult result,
			String recordType,
			String simulationName,
			SimulationRecordSettings settings) {
		try {
			String resultJson = OBJECT_MAPPER.writeValueAsString(result);
			String settingsJson = settings == null ? null : OBJECT_MAPPER.writeValueAsString(settings);
			long id = forecastRecordRepository.insert(
					calculatedAt,
					forecastHours,
					resultJson,
					recordType,
					simulationName,
					settingsJson);
			return new ForecastRecordDetail(id, calculatedAt, forecastHours, recordType, simulationName, settings, result);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to save forecast record", ex);
		}
	}

	public List<ForecastRecordSummary> listSummaries(String recordType) {
		return forecastRecordRepository.findSummariesByType(recordType);
	}

	public ForecastRecordDetail getDetail(long id) {
		ForecastRecordRepository.StoredForecastRecord stored = forecastRecordRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Forecast record not found: " + id));
		try {
			RiverNetworkForecastResult result = normalizeResult(OBJECT_MAPPER.readValue(
					stored.resultJson(),
					RiverNetworkForecastResult.class));
			SimulationRecordSettings settings = readSettings(stored.settingsJson());
			if (settings == null && isScenarioRunRecord(stored.recordType())) {
				settings = fallbackSettings(stored, result);
			}
			return new ForecastRecordDetail(
					stored.id(),
					stored.calculatedAt(),
					stored.forecastHours(),
					stored.recordType(),
					stored.simulationName(),
					settings,
					result);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to read forecast record: " + id, ex);
		}
	}

	public void delete(long id) {
		if (!forecastRecordRepository.deleteById(id)) {
			throw new IllegalArgumentException("Forecast record not found: " + id);
		}
	}

	private SimulationRecordSettings readSettings(String settingsJson) throws Exception {
		if (settingsJson == null || settingsJson.isBlank()) {
			return null;
		}
		return OBJECT_MAPPER.readValue(settingsJson, SimulationRecordSettings.class);
	}

	private boolean isScenarioRunRecord(String recordType) {
		return "simulation".equalsIgnoreCase(recordType) || "plan".equalsIgnoreCase(recordType);
	}

	private SimulationRecordSettings fallbackSettings(
			ForecastRecordRepository.StoredForecastRecord stored,
			RiverNetworkForecastResult result) {
		return new SimulationRecordSettings(
				stored.simulationName(),
				stored.calculatedAt(),
				stored.forecastHours(),
				result.dt(),
				result.nSteps(),
				result.status(),
				result.boundaryValues(),
				null,
				null);
	}

	private RiverNetworkForecastResult normalizeResult(RiverNetworkForecastResult result) {
		List<RiverNetworkReachHistory> reachHistories = result.reachHistories();
		if (reachHistories != null) {
			return result;
		}
		return new RiverNetworkForecastResult(
				result.status(),
				result.forecastHours(),
				result.nSteps(),
				result.dt(),
				result.simulatedSeconds(),
				result.timestamp(),
				result.nodeHeads(),
				result.nodeFlows(),
				result.boundaryValues(),
				result.nodes(),
				result.nodeHistories(),
				result.reaches(),
				result.reachProfiles(),
				List.of());
	}
}
