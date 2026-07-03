package com.project.waternet.weather;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.waternet.weather.dto.EnvironmentSnapshot;

@Service
public class QWeatherBridgeService {

	private static final Logger log = LoggerFactory.getLogger(QWeatherBridgeService.class);
	private static final Pattern JSON_LINE = Pattern.compile("\\{.*\\}");
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final Path scriptPath;
	private final String pythonCommand;

	public QWeatherBridgeService(
			@Value("${waternet.weather.python-command:python}") String pythonCommand) {
		this.pythonCommand = pythonCommand;
		this.scriptPath = Path.of(System.getProperty("user.dir"), "scripts", "qweather_service.py");
	}

	public EnvironmentSnapshot fetchEnvironmentSnapshot() {
		try {
			ProcessBuilder builder = new ProcessBuilder(pythonCommand, scriptPath.toString(), "--json-summary");
			builder.environment().put("PYTHONIOENCODING", "utf-8");
			builder.redirectErrorStream(true);
			Process process = builder.start();

			String output;
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				output = reader.lines().reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
			}

			if (!process.waitFor(20, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				throw new IllegalStateException("Python weather script timed out");
			}
			if (process.exitValue() != 0) {
				throw new IllegalStateException("Python weather script failed: " + output);
			}

			String jsonLine = extractJsonLine(output);
			JsonNode node = OBJECT_MAPPER.readTree(jsonLine);
			return new EnvironmentSnapshot(
					text(node, "weatherText"),
					text(node, "temperature"),
					text(node, "windSpeed"),
					text(node, "windScale"),
					text(node, "observedAt"));
		} catch (Exception ex) {
			log.warn("Weather script unavailable, using fallback snapshot: {}", ex.getMessage());
			return fallbackSnapshot();
		}
	}

	private String extractJsonLine(String output) {
		for (String line : output.split("\\R")) {
			Matcher matcher = JSON_LINE.matcher(line.trim());
			if (matcher.matches()) {
				return matcher.group();
			}
		}
		throw new IllegalStateException("No JSON payload found in weather script output");
	}

	private String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? "--" : value.asText("--");
	}

	private EnvironmentSnapshot fallbackSnapshot() {
		return new EnvironmentSnapshot("晴", "25.6", "3.2", "2", LocalDateTime.now().toString());
	}
}
