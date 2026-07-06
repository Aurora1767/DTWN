package com.project.waternet.rivernetwork;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.waternet.rivernetwork.dto.RiverNetworkForecastResult;
import com.project.waternet.rivernetwork.dto.RiverNetworkNodeHistory;
import com.project.waternet.rivernetwork.dto.RiverNetworkNodeResult;
import com.project.waternet.rivernetwork.dto.RiverNetworkReachHistory;
import com.project.waternet.rivernetwork.dto.RiverNetworkReachProfile;
import com.project.waternet.rivernetwork.dto.RiverNetworkReachResult;

@Service
public class RiverNetworkBridgeService {

	private static final Logger log = LoggerFactory.getLogger(RiverNetworkBridgeService.class);
	private static final Pattern JSON_LINE = Pattern.compile("\\{.*\\}");
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final double DEFAULT_FORECAST_HOURS = 1.0;
	private static final double DEFAULT_DT = 300.0;
	private static final long PYTHON_LOCK_WAIT_SECONDS = 5L;

	private final Path scriptPath;
	private final String pythonCommand;
	private final Semaphore pythonExecutionLock = new Semaphore(1);

	public RiverNetworkBridgeService(
			@Value("${waternet.water-quantity.python-command:python}") String pythonCommand) {
		this.pythonCommand = pythonCommand;
		this.scriptPath = Path.of(System.getProperty("user.dir"), "scripts", "river_network_forecast_service.py");
	}

	public RiverNetworkForecastResult runForecast(double forecastHours) {
		double normalizedHours = forecastHours > 0 ? forecastHours : DEFAULT_FORECAST_HOURS;
		try {
			return executeScript(buildBaseCommand(normalizedHours, DEFAULT_DT), normalizedHours, true);
		} catch (Exception ex) {
			log.warn("River network forecast unavailable, using fallback result: {}", ex.getMessage());
			return fallbackResult(normalizedHours);
		}
	}

	public RiverNetworkForecastResult runSimulation(
			double forecastHours,
			Map<String, Double> boundaryValues,
			Map<String, List<Double>> boundarySeries,
			Map<String, Double> initialNodeHeadsOverride,
			String simulationStartAt,
			double dt,
			Map<String, Double> gateOpenings) {
		double normalizedHours = forecastHours > 0 ? forecastHours : DEFAULT_FORECAST_HOURS;
		double normalizedDt = dt > 0 ? dt : DEFAULT_DT;
		List<Path> tempFiles = new ArrayList<>();
		try {
			List<String> command = buildBaseCommand(normalizedHours, normalizedDt);
			if (simulationStartAt != null && !simulationStartAt.isBlank()) {
				command.add("--simulation-start-at");
				command.add(simulationStartAt);
			}
			appendJsonFileArg(command, tempFiles, "--boundary-file", boundaryValues);
			appendJsonFileArg(command, tempFiles, "--boundary-series-file", boundarySeries);
			appendJsonFileArg(command, tempFiles, "--node-heads-file", initialNodeHeadsOverride);
			appendJsonFileArg(command, tempFiles, "--gate-openings-file", gateOpenings);
			return executeScript(command, normalizedHours, false);
		} catch (Exception ex) {
			throw new IllegalStateException("River network simulation failed: " + ex.getMessage(), ex);
		} finally {
			deleteTempFiles(tempFiles);
		}
	}

	private List<String> buildBaseCommand(double forecastHours, double dt) {
		List<String> command = new ArrayList<>();
		command.add(pythonCommand);
		command.add(scriptPath.toString());
		command.add("--json");
		command.add("--hours");
		command.add(String.valueOf(forecastHours));
		command.add("--dt");
		command.add(String.valueOf(dt));
		return command;
	}

	private RiverNetworkForecastResult executeScript(
			List<String> command,
			double forecastHours,
			boolean allowFallbackOnError) throws Exception {
		if (!pythonExecutionLock.tryAcquire(PYTHON_LOCK_WAIT_SECONDS, TimeUnit.SECONDS)) {
			throw new IllegalStateException("河网模型正在计算中，请稍后再试");
		}

		try {
			return runProcess(command, forecastHours, allowFallbackOnError);
		} finally {
			pythonExecutionLock.release();
		}
	}

	private RiverNetworkForecastResult runProcess(
			List<String> command,
			double forecastHours,
			boolean allowFallbackOnError) throws Exception {
		int timeoutSeconds = (int) Math.max(180, Math.ceil(forecastHours * 45));

		ProcessBuilder builder = new ProcessBuilder(command);
		builder.environment().put("PYTHONIOENCODING", "utf-8");
		builder.redirectErrorStream(true);
		Process process = builder.start();

		String output;
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			output = reader.lines().reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
		}

		if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			throw new IllegalStateException("River network forecast script timed out");
		}
		if (process.exitValue() != 0) {
			throw new IllegalStateException("River network forecast script failed: " + output);
		}

		String jsonLine = extractJsonLine(output);
		JsonNode node = OBJECT_MAPPER.readTree(jsonLine);
		if (!"success".equals(text(node, "status"))) {
			String message = text(node, "message").isBlank()
					? "River network forecast returned error status"
					: text(node, "message");
			if (allowFallbackOnError) {
				throw new IllegalStateException(message);
			}
			return new RiverNetworkForecastResult(
					text(node, "status"),
					number(node, "forecastHours"),
					node.path("nSteps").asInt(0),
					number(node, "dt"),
					number(node, "simulatedSeconds"),
					text(node, "timestamp"),
					readNumberMap(node.path("nodeHeads")),
					readNumberMap(node.path("nodeFlows")),
					readNumberMap(node.path("boundaryValues")),
					readNodes(node.path("nodes")),
					readNodeHistories(node.path("nodeHistories")),
					readReaches(node.path("reaches")),
					readReachProfiles(node.path("reachProfiles")),
					readReachHistories(node.path("reachHistories")));
		}
		return parseResult(node);
	}

	private void appendJsonFileArg(
			List<String> command,
			List<Path> tempFiles,
			String flag,
			Object payload) throws IOException {
		if (payload == null) {
			return;
		}
		if (payload instanceof Map<?, ?> map && map.isEmpty()) {
			return;
		}
		Path tempFile = Files.createTempFile("waternet-river-network-", ".json");
		Files.writeString(tempFile, OBJECT_MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8);
		tempFiles.add(tempFile);
		command.add(flag);
		command.add(tempFile.toAbsolutePath().toString());
	}

	private void deleteTempFiles(List<Path> tempFiles) {
		for (Path tempFile : tempFiles) {
			try {
				Files.deleteIfExists(tempFile);
			} catch (IOException ex) {
				log.debug("Failed to delete temp file {}: {}", tempFile, ex.getMessage());
			}
		}
	}

	private RiverNetworkForecastResult parseResult(JsonNode node) {
		return new RiverNetworkForecastResult(
				text(node, "status"),
				number(node, "forecastHours"),
				node.path("nSteps").asInt(1),
				number(node, "dt"),
				number(node, "simulatedSeconds"),
				text(node, "timestamp"),
				readNumberMap(node.path("nodeHeads")),
				readNumberMap(node.path("nodeFlows")),
				readNumberMap(node.path("boundaryValues")),
				readNodes(node.path("nodes")),
				readNodeHistories(node.path("nodeHistories")),
				readReaches(node.path("reaches")),
				readReachProfiles(node.path("reachProfiles")),
				readReachHistories(node.path("reachHistories")));
	}

	private List<RiverNetworkNodeResult> readNodes(JsonNode node) {
		List<RiverNetworkNodeResult> nodes = new ArrayList<>();
		if (!node.isArray()) {
			return nodes;
		}
		for (JsonNode item : node) {
			nodes.add(new RiverNetworkNodeResult(
					item.path("nodeId").asInt(),
					number(item, "waterLevel"),
					number(item, "netFlow")));
		}
		return nodes;
	}

	private List<RiverNetworkNodeHistory> readNodeHistories(JsonNode node) {
		List<RiverNetworkNodeHistory> histories = new ArrayList<>();
		if (!node.isArray()) {
			return histories;
		}
		for (JsonNode item : node) {
			histories.add(new RiverNetworkNodeHistory(
					item.path("nodeId").asInt(),
					readNumberList(item.path("waterLevels")),
					readNumberList(item.path("netFlows"))));
		}
		return histories;
	}

	private List<RiverNetworkReachResult> readReaches(JsonNode node) {
		List<RiverNetworkReachResult> reaches = new ArrayList<>();
		if (!node.isArray()) {
			return reaches;
		}
		for (JsonNode item : node) {
			reaches.add(new RiverNetworkReachResult(
					item.path("reachId").asInt(),
					item.path("startNode").asInt(),
					item.path("endNode").asInt(),
					number(item, "length"),
					number(item, "width"),
					number(item, "inletFlow"),
					number(item, "outletFlow"),
					number(item, "avgWaterLevel"),
					number(item, "maxWaterLevel"),
					number(item, "minWaterLevel"),
					number(item, "avgFlow")));
		}
		return reaches;
	}

	private List<RiverNetworkReachHistory> readReachHistories(JsonNode node) {
		List<RiverNetworkReachHistory> histories = new ArrayList<>();
		if (!node.isArray()) {
			return histories;
		}
		for (JsonNode item : node) {
			histories.add(new RiverNetworkReachHistory(
					item.path("reachId").asInt(),
					item.path("startNode").asInt(),
					item.path("endNode").asInt(),
					text(item, "label"),
					readNumberList(item.path("inletFlows")),
					readNumberList(item.path("outletFlows")),
					readNumberList(item.path("inletWaterLevels")),
					readNumberList(item.path("outletWaterLevels"))));
		}
		return histories;
	}

	private List<RiverNetworkReachProfile> readReachProfiles(JsonNode node) {
		List<RiverNetworkReachProfile> profiles = new ArrayList<>();
		if (!node.isArray()) {
			return profiles;
		}
		for (JsonNode item : node) {
			profiles.add(new RiverNetworkReachProfile(
					item.path("reachId").asInt(),
					item.path("startNode").asInt(),
					item.path("endNode").asInt(),
					text(item, "label"),
					number(item, "length"),
					readNumberList(item.path("distances")),
					readNumberList(item.path("waterLevels")),
					readNumberList(item.path("flows"))));
		}
		return profiles;
	}

	private List<Double> readNumberList(JsonNode node) {
		List<Double> values = new ArrayList<>();
		if (!node.isArray()) {
			return values;
		}
		for (JsonNode item : node) {
			values.add(item.asDouble(0.0));
		}
		return values;
	}

	private Map<String, Double> readNumberMap(JsonNode node) {
		Map<String, Double> values = new LinkedHashMap<>();
		if (!node.isObject()) {
			return values;
		}
		node.fields().forEachRemaining(entry -> values.put(entry.getKey(), entry.getValue().asDouble(0.0)));
		return values;
	}

	private String extractJsonLine(String output) {
		String candidate = output.trim();
		if (candidate.startsWith("{")) {
			return candidate;
		}
		String lastJson = null;
		for (String line : output.split("\\R")) {
			Matcher matcher = JSON_LINE.matcher(line.trim());
			if (matcher.matches()) {
				lastJson = matcher.group();
			}
		}
		if (lastJson != null) {
			return lastJson;
		}
		throw new IllegalStateException("No JSON payload found in river network forecast script output");
	}

	private String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? "" : value.asText("");
	}

	private double number(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? 0.0 : value.asDouble(0.0);
	}

	private RiverNetworkForecastResult fallbackResult(double forecastHours) {
		int nSteps = (int) Math.max(1, Math.round(forecastHours * 3600.0 / DEFAULT_DT));
		Map<String, Double> nodeHeads = new LinkedHashMap<>();
		nodeHeads.put("1", 13.2);
		nodeHeads.put("3", 13.3);
		nodeHeads.put("6", 13.3);
		List<RiverNetworkNodeResult> nodes = List.of(
				new RiverNetworkNodeResult(1, 13.2, -12.5),
				new RiverNetworkNodeResult(3, 13.3, -8.2),
				new RiverNetworkNodeResult(6, 13.3, 34.8));
		List<RiverNetworkReachResult> reaches = List.of(
				new RiverNetworkReachResult(1, 1, 2, 1400.0, 20.0, -12.5, -11.8, 13.0, 13.2, 12.8, 12.1));
		List<RiverNetworkReachProfile> profiles = List.of(
				new RiverNetworkReachProfile(
						1,
						1,
						2,
						"河段1 (1-2)",
						1400.0,
						List.of(0.0, 700.0, 1400.0),
						List.of(13.2, 13.0, 12.8),
						List.of(-12.5, -12.1, -11.8)));
		List<RiverNetworkReachHistory> reachHistories = List.of(
				new RiverNetworkReachHistory(
						1,
						1,
						2,
						"河段1 (1-2)",
						List.of(-12.5, -12.3, -12.1),
						List.of(-11.8, -11.6, -11.4),
						List.of(13.2, 13.18, 13.15),
						List.of(12.8, 12.78, 12.75)));
		List<RiverNetworkNodeHistory> nodeHistories = List.of(
				new RiverNetworkNodeHistory(
						1,
						List.of(13.2, 13.18, 13.15),
						List.of(-12.5, -12.3, -12.1)),
				new RiverNetworkNodeHistory(
						3,
						List.of(13.3, 13.28, 13.25),
						List.of(-8.2, -8.0, -7.8)),
				new RiverNetworkNodeHistory(
						6,
						List.of(13.3, 13.32, 13.35),
						List.of(34.8, 35.0, 35.2)));
		return new RiverNetworkForecastResult(
				"fallback",
				forecastHours,
				nSteps,
				DEFAULT_DT,
				nSteps * DEFAULT_DT,
				Instant.now().toString(),
				nodeHeads,
				Map.of("1", -12.5, "3", -8.2, "6", 34.8),
				Map.of("1", 13.2, "3", 13.3, "6", -5.0),
				nodes,
				nodeHistories,
				reaches,
				profiles,
				reachHistories);
	}
}
