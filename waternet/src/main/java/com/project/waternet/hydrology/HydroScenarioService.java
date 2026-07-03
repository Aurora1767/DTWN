package com.project.waternet.hydrology;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.waternet.hydrology.dto.HydroChannelValue;
import com.project.waternet.hydrology.dto.HydroScenarioSnapshot;

@Service
public class HydroScenarioService {

	private static final Logger log = LoggerFactory.getLogger(HydroScenarioService.class);
	private static final String API_URL = "https://waterlevel.gd.hydrosim.cn/api/scenario/latest";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	public HydroScenarioSnapshot fetchLatest() {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
					.timeout(Duration.ofSeconds(8))
					.GET()
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IllegalStateException("Hydro scenario API returned " + response.statusCode());
			}
			return parseSnapshot(response.body());
		} catch (Exception ex) {
			log.warn("Hydro scenario API unavailable, using fallback values: {}", ex.getMessage());
			return fallbackSnapshot();
		}
	}

	private HydroScenarioSnapshot parseSnapshot(String body) throws Exception {
		JsonNode root = OBJECT_MAPPER.readTree(body);
		JsonNode channels = root.path("channels");
		return new HydroScenarioSnapshot(Map.of(
				"taihu", readChannel(channels, "taihu"),
				"canal-north", readChannel(channels, "canal-north"),
				"canal-south", readChannel(channels, "canal-south")));
	}

	private HydroChannelValue readChannel(JsonNode channels, String key) {
		JsonNode node = channels.path(key);
		return new HydroChannelValue(
				node.path("value").asDouble(0),
				node.path("timestamp").asText(OffsetDateTime.now().toString()));
	}

	private HydroScenarioSnapshot fallbackSnapshot() {
		String now = OffsetDateTime.now().toString();
		return new HydroScenarioSnapshot(Map.of(
				"taihu", new HydroChannelValue(14.02, now),
				"canal-north", new HydroChannelValue(132.04, now),
				"canal-south", new HydroChannelValue(14.03, now)));
	}
}
