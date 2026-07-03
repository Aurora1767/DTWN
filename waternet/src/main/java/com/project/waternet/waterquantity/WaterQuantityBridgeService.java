package com.project.waternet.waterquantity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.waternet.waterquantity.dto.WaterHistoryPoint;
import com.project.waternet.waterquantity.dto.WaterQuantityOverview;
import com.project.waternet.waterquantity.dto.WaterStationSnapshot;

@Service
public class WaterQuantityBridgeService {

	private static final Logger log = LoggerFactory.getLogger(WaterQuantityBridgeService.class);
	private static final Pattern JSON_LINE = Pattern.compile("\\{.*\\}");
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final long CACHE_TTL_MS = 5 * 60 * 1000;

	private final Path scriptPath;
	private final String pythonCommand;
	private volatile WaterQuantityOverview cachedOverview;
	private volatile long cachedAtMs;

	public WaterQuantityBridgeService(
			@Value("${waternet.water-quantity.python-command:python}") String pythonCommand) {
		this.pythonCommand = pythonCommand;
		this.scriptPath = Path.of(System.getProperty("user.dir"), "scripts", "water_quantity_service.py");
	}

	public WaterQuantityOverview fetchOverview() {
		if (cachedOverview != null && Instant.now().toEpochMilli() - cachedAtMs < CACHE_TTL_MS) {
			return cachedOverview;
		}
		WaterQuantityOverview overview = loadOverview(false);
		cachedOverview = overview;
		cachedAtMs = Instant.now().toEpochMilli();
		return overview;
	}

	public List<WaterStationSnapshot> fetchStations() {
		return fetchOverview().stations();
	}

	public List<WaterHistoryPoint> fetchStationHistory(String stationCode) {
		return fetchOverview().historyByCode().getOrDefault(stationCode, List.of());
	}

	private WaterQuantityOverview loadOverview(boolean cacheOnly) {
		try {
			List<String> command = new ArrayList<>();
			command.add(pythonCommand);
			command.add(scriptPath.toString());
			command.add("--json");
			if (cacheOnly) {
				command.add("--cache-only");
			}

			ProcessBuilder builder = new ProcessBuilder(command);
			builder.environment().put("PYTHONIOENCODING", "utf-8");
			builder.redirectErrorStream(true);
			Process process = builder.start();

			String output;
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				output = reader.lines().reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
			}

			if (!process.waitFor(120, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				throw new IllegalStateException("Python water quantity script timed out");
			}
			if (process.exitValue() != 0) {
				throw new IllegalStateException("Python water quantity script failed: " + output);
			}

			String jsonLine = extractJsonLine(output);
			JsonNode node = OBJECT_MAPPER.readTree(jsonLine);
			return parseOverview(node);
		} catch (Exception ex) {
			if (cacheOnly) {
				throw new IllegalStateException("Water quantity cache fallback failed", ex);
			}
			log.warn("Water quantity script unavailable, trying cache-only fallback: {}", ex.getMessage());
			try {
				return loadOverview(true);
			} catch (Exception cacheEx) {
				log.warn("Water quantity cache fallback failed, using embedded fallback: {}", cacheEx.getMessage());
				return fallbackOverview();
			}
		}
	}

	private WaterQuantityOverview parseOverview(JsonNode node) {
		List<WaterStationSnapshot> stations = new ArrayList<>();
		JsonNode stationsNode = node.path("stations");
		if (stationsNode.isArray()) {
			for (JsonNode item : stationsNode) {
				stations.add(new WaterStationSnapshot(
						text(item, "stationCode"),
						text(item, "stationName"),
						number(item, "waterLevel"),
						number(item, "flowRate"),
						text(item, "observedAt")));
			}
		}

		Map<String, List<WaterHistoryPoint>> historyByCode = new LinkedHashMap<>();
		JsonNode historyNode = node.path("historyByCode");
		if (historyNode.isObject()) {
			historyNode.fields().forEachRemaining(entry -> {
				List<WaterHistoryPoint> points = new ArrayList<>();
				if (entry.getValue().isArray()) {
					for (JsonNode item : entry.getValue()) {
						points.add(new WaterHistoryPoint(
								text(item, "date"),
								number(item, "waterLevel"),
								number(item, "flowRate"),
								number(item, "rainfall")));
					}
				}
				historyByCode.put(entry.getKey(), points);
			});
		}

		return new WaterQuantityOverview(
				text(node, "status"),
				text(node, "timestamp"),
				node.path("live").asBoolean(false),
				stations,
				historyByCode);
	}

	private String extractJsonLine(String output) {
		String candidate = output.trim();
		if (candidate.startsWith("{")) {
			return candidate;
		}
		for (String line : output.split("\\R")) {
			Matcher matcher = JSON_LINE.matcher(line.trim());
			if (matcher.matches()) {
				return matcher.group();
			}
		}
		throw new IllegalStateException("No JSON payload found in water quantity script output");
	}

	private String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? "" : value.asText("");
	}

	private double number(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? 0.0 : value.asDouble(0.0);
	}

	private WaterQuantityOverview fallbackOverview() {
		WaterStationSnapshot station = new WaterStationSnapshot(
				"63202900",
				"望亭（大）",
				3.58,
				59.4,
				"2026-07-02");
		WaterHistoryPoint point = new WaterHistoryPoint("2026-07-02", 3.58, 59.4, 4.2);
		return new WaterQuantityOverview(
				"success",
				Instant.now().toString(),
				false,
				List.of(station),
				Map.of("63202900", List.of(point)));
	}
}
