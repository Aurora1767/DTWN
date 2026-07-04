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
import com.project.waternet.weather.dto.WeatherForecast;
import com.project.waternet.weather.dto.WeatherForecast.DailyForecast;
import com.project.waternet.weather.dto.WeatherForecast.MinutelyPrecip;

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


	public WeatherForecast fetchWeatherForecast() {
		try {
			ProcessBuilder builder = new ProcessBuilder(pythonCommand, scriptPath.toString(), "--json-forecast");
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
				throw new IllegalStateException("forecast timeout");
			}
			if (process.exitValue() != 0) throw new IllegalStateException("forecast failed: " + output);
			String jsonLine = extractJsonLine(output);
			JsonNode root = OBJECT_MAPPER.readTree(jsonLine);
			java.util.List<DailyForecast> dailyList = new java.util.ArrayList<>();
			JsonNode dailyArr = root.get("daily");
			if (dailyArr != null && dailyArr.isArray()) {
				for (JsonNode d : dailyArr) {
					dailyList.add(new DailyForecast(text(d,"fxDate"),text(d,"textDay"),text(d,"textNight"),text(d,"tempMax"),text(d,"tempMin"),text(d,"windSpeedDay"),text(d,"windScaleDay"),text(d,"windDirDay"),text(d,"precip"),text(d,"humidity")));
				}
			}
			java.util.List<MinutelyPrecip> minutelyList = new java.util.ArrayList<>();
			JsonNode minutelyArr = root.get("minutely");
			if (minutelyArr != null && minutelyArr.isArray()) {
				for (JsonNode m : minutelyArr) {
					minutelyList.add(new MinutelyPrecip(text(m,"fxTime"),text(m,"precip"),text(m,"type")));
				}
			}
			return new WeatherForecast(dailyList, minutelyList, LocalDateTime.now().toString());
		} catch (Exception ex) {
			log.warn("Weather forecast unavailable: {}", ex.getMessage());
			return fallbackForecast();
		}
	}

	private WeatherForecast fallbackForecast() {
		String today = java.time.LocalDate.now().toString();
		String tomorrow = java.time.LocalDate.now().plusDays(1).toString();
		String day3 = java.time.LocalDate.now().plusDays(2).toString();
		java.util.List<DailyForecast> daily = java.util.List.of(
			new DailyForecast(today, "多云", "阴", "28", "22", "3.5", "2", "东南风", "2.0", "72"),
			new DailyForecast(tomorrow, "小雨", "中雨", "26", "20", "4.2", "3", "东风", "12.5", "85"),
			new DailyForecast(day3, "阴", "多云", "27", "21", "2.8", "2", "北风", "0.5", "68"));
		java.util.List<MinutelyPrecip> minutely = new java.util.ArrayList<>();
		java.time.LocalDateTime now = java.time.LocalDateTime.now();
		for (int i = 0; i < 24; i++) {
			String t = now.plusMinutes(i * 5L).toString();
			String p = i < 4 ? "0.0" : (i < 12 ? String.format("%.1f", 0.3 + i * 0.2) : "0.0");
			minutely.add(new MinutelyPrecip(t, p, "rain"));
		}
		return new WeatherForecast(daily, minutely, now.toString());
	}


	public WeatherForecast fetchWeatherForecast() {
		try {
			ProcessBuilder builder = new ProcessBuilder(pythonCommand, scriptPath.toString(), "--json-forecast");
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
				throw new IllegalStateException("forecast timeout");
			}
			if (process.exitValue() != 0) throw new IllegalStateException("forecast failed: " + output);
			String jsonLine = extractJsonLine(output);
			JsonNode root = OBJECT_MAPPER.readTree(jsonLine);
			java.util.List<DailyForecast> dailyList = new java.util.ArrayList<>();
			JsonNode dailyArr = root.get("daily");
			if (dailyArr != null && dailyArr.isArray()) {
				for (JsonNode d : dailyArr) {
					dailyList.add(new DailyForecast(text(d,"fxDate"),text(d,"textDay"),text(d,"textNight"),text(d,"tempMax"),text(d,"tempMin"),text(d,"windSpeedDay"),text(d,"windScaleDay"),text(d,"windDirDay"),text(d,"precip"),text(d,"humidity")));
				}
			}
			java.util.List<MinutelyPrecip> minutelyList = new java.util.ArrayList<>();
			JsonNode minutelyArr = root.get("minutely");
			if (minutelyArr != null && minutelyArr.isArray()) {
				for (JsonNode m : minutelyArr) {
					minutelyList.add(new MinutelyPrecip(text(m,"fxTime"),text(m,"precip"),text(m,"type")));
				}
			}
			return new WeatherForecast(dailyList, minutelyList, LocalDateTime.now().toString());
		} catch (Exception ex) {
			log.warn("Weather forecast unavailable: {}", ex.getMessage());
			return fallbackForecast();
		}
	}

	private WeatherForecast fallbackForecast() {
		String today = java.time.LocalDate.now().toString();
		String tomorrow = java.time.LocalDate.now().plusDays(1).toString();
		String day3 = java.time.LocalDate.now().plusDays(2).toString();
		java.util.List<DailyForecast> daily = java.util.List.of(
			new DailyForecast(today, "多云", "阴", "28", "22", "3.5", "2", "东南风", "2.0", "72"),
			new DailyForecast(tomorrow, "小雨", "中雨", "26", "20", "4.2", "3", "东风", "12.5", "85"),
			new DailyForecast(day3, "阴", "多云", "27", "21", "2.8", "2", "北风", "0.5", "68"));
		java.util.List<MinutelyPrecip> minutely = new java.util.ArrayList<>();
		java.time.LocalDateTime now = java.time.LocalDateTime.now();
		for (int i = 0; i < 24; i++) {
			String t = now.plusMinutes(i * 5L).toString();
			String p = i < 4 ? "0.0" : (i < 12 ? String.format("%.1f", 0.3 + i * 0.2) : "0.0");
			minutely.add(new MinutelyPrecip(t, p, "rain"));
		}
		return new WeatherForecast(daily, minutely, now.toString());
	}
}
