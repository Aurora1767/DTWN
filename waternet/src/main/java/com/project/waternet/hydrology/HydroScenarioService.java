package com.project.waternet.hydrology;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.waternet.hydrology.dto.HydroBoundarySnapshot;
import com.project.waternet.hydrology.dto.HydroChannelValue;
import com.project.waternet.hydrology.dto.HydroScenarioRecord;
import com.project.waternet.hydrology.dto.HydroScenarioSnapshot;

@Service
public class HydroScenarioService {

	private static final Logger log = LoggerFactory.getLogger(HydroScenarioService.class);
	private static final String LATEST_API_URL = "https://waterlevel.gd.hydrosim.cn/api/scenario/latest";
	private static final String MONITORING_BASE_URL = "https://waterlevel.gd.hydrosim.cn/api/scenario";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final long CACHE_TTL_MS = 30_000L;
	private static final long MAX_BOUNDARY_MATCH_MS = Duration.ofMinutes(30).toMillis();

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();
	private final Map<String, CacheEntry> mergedCache = new ConcurrentHashMap<>();

	public HydroScenarioSnapshot fetchLatest() {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST_API_URL))
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

	public List<HydroScenarioRecord> fetchMergedRecords(String startDate, String endDate, int pageSize) {
		int limit = pageSize <= 0 ? 100 : Math.min(pageSize, 5000);
		String cacheKey = String.format("%s_%s_%d",
				Optional.ofNullable(startDate).orElse(""),
				Optional.ofNullable(endDate).orElse(""),
				limit);

		CacheEntry entry = mergedCache.get(cacheKey);
		if (entry != null && System.currentTimeMillis() - entry.timestamp < CACHE_TTL_MS) {
			return entry.records;
		}

		CompletableFuture<List<MonitoringRecord>> taihuFuture = CompletableFuture
				.supplyAsync(() -> fetchChannelRecords("taihu", startDate, endDate, limit));
		CompletableFuture<List<MonitoringRecord>> northFuture = CompletableFuture
				.supplyAsync(() -> fetchChannelRecords("canal-north", startDate, endDate, limit));
		CompletableFuture<List<MonitoringRecord>> southFuture = CompletableFuture
				.supplyAsync(() -> fetchChannelRecords("canal-south", startDate, endDate, limit));

		CompletableFuture.allOf(taihuFuture, northFuture, southFuture).join();

		try {
			Map<String, HydroScenarioRecordBuilder> mergedMap = new TreeMap<>(Comparator.reverseOrder());

			for (MonitoringRecord record : taihuFuture.get()) {
				if (record.timestamp() != null) {
					mergedMap.computeIfAbsent(record.timestamp(), HydroScenarioRecordBuilder::new)
							.taihuValue = record.value();
				}
			}
			for (MonitoringRecord record : northFuture.get()) {
				if (record.timestamp() != null) {
					mergedMap.computeIfAbsent(record.timestamp(), HydroScenarioRecordBuilder::new)
							.canalNorthValue = record.value();
				}
			}
			for (MonitoringRecord record : southFuture.get()) {
				if (record.timestamp() != null) {
					mergedMap.computeIfAbsent(record.timestamp(), HydroScenarioRecordBuilder::new)
							.canalSouthValue = record.value();
				}
			}

			List<HydroScenarioRecord> records = mergedMap.values().stream()
					.map(HydroScenarioRecordBuilder::build)
					.toList();
			mergedCache.put(cacheKey, new CacheEntry(System.currentTimeMillis(), records));
			return records;
		} catch (Exception ex) {
			throw new IllegalStateException("合并太湖与京杭运河通道数据失败: " + ex.getMessage(), ex);
		}
	}

	public HydroBoundarySnapshot resolveBoundaryAt(String atIso) {
		Instant target = parseInstant(atIso);
		LocalDate targetDate = target.atZone(ZoneId.systemDefault()).toLocalDate();
		String startDate = targetDate.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
		String endDate = targetDate.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);

		List<HydroScenarioRecord> records = fetchMergedRecords(startDate, endDate, 500);
		HydroScenarioRecord matched = findClosestRecord(records, target.toEpochMilli())
				.orElseThrow(() -> new IllegalArgumentException(
						"未找到预演开始时间附近的边界监测数据，请调整时间或稍后重试"));

		if (matched.taihuValue() == null || matched.canalNorthValue() == null || matched.canalSouthValue() == null) {
			throw new IllegalArgumentException("选定时刻的三通道边界数据不完整，请选择其他时刻");
		}

		return new HydroBoundarySnapshot(
				matched.timestamp(),
				toBoundaryValues(matched));
	}

	public Map<String, Double> toBoundaryValues(HydroScenarioRecord record) {
		return Map.of(
				"1", record.taihuValue(),
				"3", record.canalSouthValue(),
				"6", -record.canalNorthValue());
	}

	private Optional<HydroScenarioRecord> findClosestRecord(List<HydroScenarioRecord> records, long targetMs) {
		HydroScenarioRecord closest = null;
		long closestDiff = Long.MAX_VALUE;

		for (HydroScenarioRecord record : records) {
			if (record.taihuValue() == null || record.canalNorthValue() == null || record.canalSouthValue() == null) {
				continue;
			}
			long recordMs = parseInstant(record.timestamp()).toEpochMilli();
			long diff = Math.abs(recordMs - targetMs);
			if (diff < closestDiff) {
				closestDiff = diff;
				closest = record;
			}
		}

		if (closest == null || closestDiff > MAX_BOUNDARY_MATCH_MS) {
			return Optional.empty();
		}
		return Optional.of(closest);
	}

	private List<MonitoringRecord> fetchChannelRecords(String channel, String startDate, String endDate, int pageSize) {
		String url = MONITORING_BASE_URL + "/" + channel + "/monitoring";
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url)
				.queryParam("page", 1)
				.queryParam("page_size", pageSize)
				.queryParam("order", "desc");

		if (startDate != null && !startDate.isBlank()) {
			builder.queryParam("start_date", startDate);
		}
		if (endDate != null && !endDate.isBlank()) {
			builder.queryParam("end_date", endDate);
		}

		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(builder.toUriString()))
					.timeout(Duration.ofSeconds(10))
					.GET()
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IllegalStateException("通道 " + channel + " 返回 " + response.statusCode());
			}
			return parseMonitoringRecords(response.body());
		} catch (Exception ex) {
			log.warn("获取通道 {} 数据失败: {}", channel, ex.getMessage());
			return List.of();
		}
	}

	private List<MonitoringRecord> parseMonitoringRecords(String body) throws Exception {
		JsonNode root = OBJECT_MAPPER.readTree(body);
		JsonNode data = root.path("data");
		if (!data.isArray()) {
			return List.of();
		}
		List<MonitoringRecord> records = new ArrayList<>();
		for (JsonNode node : data) {
			String timestamp = node.path("timestamp").asText(null);
			if (timestamp == null || timestamp.isBlank()) {
				continue;
			}
			records.add(new MonitoringRecord(timestamp, node.path("value").asDouble()));
		}
		return records;
	}

	private Instant parseInstant(String value) {
		return OffsetDateTime.parse(value).toInstant();
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

	private record MonitoringRecord(String timestamp, double value) {
	}

	private static final class HydroScenarioRecordBuilder {
		private final String timestamp;
		private Double taihuValue;
		private Double canalNorthValue;
		private Double canalSouthValue;

		private HydroScenarioRecordBuilder(String timestamp) {
			this.timestamp = timestamp;
		}

		private HydroScenarioRecord build() {
			return new HydroScenarioRecord(timestamp, taihuValue, canalNorthValue, canalSouthValue);
		}
	}

	private static final class CacheEntry {
		private final long timestamp;
		private final List<HydroScenarioRecord> records;

		private CacheEntry(long timestamp, List<HydroScenarioRecord> records) {
			this.timestamp = timestamp;
			this.records = records;
		}
	}
}
