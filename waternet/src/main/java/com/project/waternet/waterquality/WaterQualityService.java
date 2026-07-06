package com.project.waternet.waterquality;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

import com.project.waternet.waterquality.dto.WaterQualityHistoryPoint;
import com.project.waternet.waterquality.dto.WaterQualityNodeHistory;
import com.project.waternet.waterquality.dto.WaterQualityOverview;
import com.project.waternet.waterquality.dto.WaterQualityPoint;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class WaterQualityService {

	private static final Set<Integer> GROUP_1 = Set.of(5, 12, 17, 24, 33, 38, 42, 49, 53, 58, 64, 71, 76, 80, 83, 87, 89, 91);
	private static final Set<Integer> GROUP_3 = Set.of(2, 7, 14, 21, 29, 36, 40, 45, 51, 56, 61, 67, 73, 78, 82, 85, 88, 92);
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final List<String> LEVELS = List.of("I类", "II类", "III类", "IV类", "V类");
	private static final LocalDateTime BACKFILL_BASELINE = LocalDateTime.of(2026, 7, 4, 12, 0);

	private final WaterQualityRepository waterQualityRepository;
	private final AtomicReference<WaterQualityOverview> cache = new AtomicReference<>();
	private ScheduledExecutorService scheduler;

	public WaterQualityService(WaterQualityRepository waterQualityRepository) {
		this.waterQualityRepository = waterQualityRepository;
	}

	@PostConstruct
	public void start() {
		scheduler = Executors.newSingleThreadScheduledExecutor((runnable) -> {
			Thread thread = new Thread(runnable, "water-quality-simulator");
			thread.setDaemon(true);
			return thread;
		});
		scheduler.execute(() -> {
			backfillMissingHours();
			refresh();
		});
		scheduler.scheduleAtFixedRate(this::refresh, 60, 60, TimeUnit.SECONDS);
	}

	@PreDestroy
	public void stop() {
		if (scheduler != null) {
			scheduler.shutdownNow();
		}
	}

	public WaterQualityOverview latest() {
		WaterQualityOverview overview = cache.get();
		if (overview == null) {
			try {
				refresh();
				overview = cache.get();
			} catch (Exception exception) {
				overview = fallbackOverview();
				cache.set(overview);
			}
		}
		return overview;
	}

	public WaterQualityNodeHistory nodeHistory(int nodeId, int hours) {
		int safeNodeId = Math.max(1, Math.min(nodeId, 92));
		List<WaterQualityHistoryPoint> points = waterQualityRepository.findHistory(safeNodeId, hours);
		if (points.isEmpty()) {
			WaterQualityPoint current = cache.get() == null
					? generateNodeData(safeNodeId)
					: cache.get().nodes().stream()
							.filter((point) -> point.nodeId() == safeNodeId)
							.findFirst()
							.orElseGet(() -> generateNodeData(safeNodeId));
			points = List.of(toHistoryPoint(current, currentHourLabel()));
		}
		return new WaterQualityNodeHistory(safeNodeId, points);
	}

	private void refresh() {
		try {
			List<WaterQualityPoint> nodes = generateAllNodes();
			Map<String, Integer> summary = summarize(nodes);
			waterQualityRepository.upsert(currentHourLabel(), nodes);
			cache.set(new WaterQualityOverview(
					"success",
					LocalDateTime.now().format(TIME_FORMATTER),
					true,
					summary,
					nodes));
		} catch (Exception exception) {
			if (cache.get() == null) {
				cache.set(fallbackOverview());
			}
		}
	}

	private void backfillMissingHours() {
		LocalDateTime cursor = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
		while (!cursor.isBefore(BACKFILL_BASELINE)) {
			String time = cursor.format(TIME_FORMATTER);
			try {
				if (waterQualityRepository.countAt(time) > 0) {
					break;
				}
				waterQualityRepository.upsert(time, generateAllNodes());
			} catch (Exception exception) {
				break;
			}
			cursor = cursor.minusHours(1);
		}
	}

	private WaterQualityPoint generateNodeData(int nodeId) {
		List<String> allowed;
		List<String> allowedCodBod;
		if (GROUP_1.contains(nodeId)) {
			allowed = List.of("I", "II", "III");
			allowedCodBod = List.of("0", "I/II", "III");
		} else if (GROUP_3.contains(nodeId)) {
			allowed = List.of("II", "III", "IV", "V");
			allowedCodBod = List.of("0", "I/II", "III", "IV", "V");
		} else {
			allowed = List.of("II", "III");
			allowedCodBod = List.of("0", "I/II", "III");
		}

		double dissolvedOxygen = sampleValue("DO", allowed);
		double permanganateIndex = sampleValue("IMn", allowed);
		double ammoniaNitrogen = sampleValue("NH3_N", allowed);
		double totalPhosphorus = sampleValue("TP", allowed);
		double chemicalOxygenDemand = sampleValue("COD", allowedCodBod);
		double bod5 = sampleValue("BOD5", allowedCodBod);

		int maxWeight = List.of(
				levelWeight("DO", dissolvedOxygen),
				levelWeight("IMn", permanganateIndex),
				levelWeight("NH3_N", ammoniaNitrogen),
				levelWeight("TP", totalPhosphorus),
				levelWeight("COD", chemicalOxygenDemand),
				levelWeight("BOD5", bod5)).stream().mapToInt(Integer::intValue).max().orElse(1);

		return new WaterQualityPoint(
				nodeId,
				randomBetween(6.0, 9.0),
				dissolvedOxygen,
				permanganateIndex,
				ammoniaNitrogen,
				totalPhosphorus,
				chemicalOxygenDemand,
				bod5,
				LEVELS.get(maxWeight - 1));
	}

	private double sampleValue(String parameter, List<String> allowedClasses) {
		String selectedClass = normalizeAndSample(parameter, allowedClasses);
		double[] interval = intervals(parameter).get(selectedClass);
		if (interval[0] == interval[1]) {
			return interval[0];
		}
		return randomBetween(interval[0], interval[1]);
	}

	private String normalizeAndSample(String parameter, List<String> allowedClasses) {
		Map<String, Double> probabilities = originalProbabilities(parameter);
		Map<String, Double> filtered = new LinkedHashMap<>();
		for (String allowedClass : allowedClasses) {
			double probability = probabilities.getOrDefault(allowedClass, 0.0);
			if (probability > 0) {
				filtered.put(allowedClass, probability);
			}
		}
		if (filtered.isEmpty()) {
			return allowedClasses.get(0);
		}

		double total = filtered.values().stream().mapToDouble(Double::doubleValue).sum();
		double random = ThreadLocalRandom.current().nextDouble(total);
		double cumulative = 0.0;
		String fallback = allowedClasses.get(0);
		for (Map.Entry<String, Double> entry : filtered.entrySet()) {
			cumulative += entry.getValue();
			fallback = entry.getKey();
			if (random <= cumulative) {
				return entry.getKey();
			}
		}
		return fallback;
	}

	private int levelWeight(String parameter, double value) {
		return switch (parameter) {
			case "DO" -> value >= 7.5 ? 1 : value >= 6.0 ? 2 : value >= 5.0 ? 3 : value >= 3.0 ? 4 : 5;
			case "IMn" -> value <= 2.0 ? 1 : value <= 4.0 ? 2 : value <= 6.0 ? 3 : value <= 10.0 ? 4 : 5;
			case "NH3_N" -> value <= 0.15 ? 1 : value <= 0.5 ? 2 : value <= 1.0 ? 3 : value <= 1.5 ? 4 : 5;
			case "TP" -> value <= 0.02 ? 1 : value <= 0.1 ? 2 : value <= 0.2 ? 3 : value <= 0.3 ? 4 : 5;
			case "COD" -> organicLevelWeight(value, 15.0, 20.0, 30.0);
			case "BOD5" -> organicLevelWeight(value, 3.0, 4.0, 6.0);
			default -> 1;
		};
	}

	private int organicLevelWeight(double value, double limit1, double limit3, double limit4) {
		if (value == 0.0 || value <= limit1) {
			return 1;
		}
		if (value <= limit3) {
			return 3;
		}
		if (value <= limit4) {
			return 4;
		}
		return 5;
	}

	private Map<String, Integer> emptySummary() {
		Map<String, Integer> summary = new LinkedHashMap<>();
		for (String level : LEVELS) {
			summary.put(level, 0);
		}
		return summary;
	}

	private List<WaterQualityPoint> generateAllNodes() {
		List<WaterQualityPoint> nodes = new ArrayList<>();
		for (int nodeId = 1; nodeId <= 92; nodeId += 1) {
			nodes.add(generateNodeData(nodeId));
		}
		return nodes;
	}

	private Map<String, Integer> summarize(List<WaterQualityPoint> nodes) {
		Map<String, Integer> summary = emptySummary();
		for (WaterQualityPoint point : nodes) {
			summary.computeIfPresent(point.level(), (key, count) -> count + 1);
		}
		return summary;
	}

	private Map<String, Double> originalProbabilities(String parameter) {
		return switch (parameter) {
			case "DO" -> orderedProbabilities(66.51, 23.26, 8.37, 1.86, 0.0);
			case "IMn" -> orderedProbabilities(17.21, 55.35, 24.19, 3.26, 0.0);
			case "NH3_N" -> orderedProbabilities(74.42, 21.86, 3.72, 0.0, 0.0);
			case "TP" -> orderedProbabilities(6.05, 76.74, 17.21, 0.0, 0.0);
			case "COD" -> orderedOrganicProbabilities(52.09, 20.47, 23.26, 4.19, 0.0);
			case "BOD5" -> orderedOrganicProbabilities(66.98, 28.37, 2.79, 1.40, 0.47);
			default -> Map.of();
		};
	}

	private Map<String, Double> orderedProbabilities(double level1, double level2, double level3, double level4, double level5) {
		Map<String, Double> probabilities = new LinkedHashMap<>();
		probabilities.put("I", level1);
		probabilities.put("II", level2);
		probabilities.put("III", level3);
		probabilities.put("IV", level4);
		probabilities.put("V", level5);
		return probabilities;
	}

	private Map<String, Double> orderedOrganicProbabilities(double zero, double level12, double level3, double level4, double level5) {
		Map<String, Double> probabilities = new LinkedHashMap<>();
		probabilities.put("0", zero);
		probabilities.put("I/II", level12);
		probabilities.put("III", level3);
		probabilities.put("IV", level4);
		probabilities.put("V", level5);
		return probabilities;
	}

	private Map<String, double[]> intervals(String parameter) {
		return switch (parameter) {
			case "DO" -> qualityIntervals(new double[] { 7.5, 12.0 }, new double[] { 6.0, 7.5 }, new double[] { 5.0, 6.0 }, new double[] { 3.0, 5.0 }, new double[] { 2.0, 3.0 });
			case "IMn" -> qualityIntervals(new double[] { 0.0, 2.0 }, new double[] { 2.0, 4.0 }, new double[] { 4.0, 6.0 }, new double[] { 6.0, 10.0 }, new double[] { 10.0, 15.0 });
			case "NH3_N" -> qualityIntervals(new double[] { 0.0, 0.15 }, new double[] { 0.15, 0.5 }, new double[] { 0.5, 1.0 }, new double[] { 1.0, 1.5 }, new double[] { 1.5, 2.0 });
			case "TP" -> qualityIntervals(new double[] { 0.0, 0.02 }, new double[] { 0.02, 0.1 }, new double[] { 0.1, 0.2 }, new double[] { 0.2, 0.3 }, new double[] { 0.3, 0.4 });
			case "COD" -> organicIntervals(new double[] { 0.0, 0.0 }, new double[] { 0.0, 15.0 }, new double[] { 15.0, 20.0 }, new double[] { 20.0, 30.0 }, new double[] { 30.0, 40.0 });
			case "BOD5" -> organicIntervals(new double[] { 0.0, 0.0 }, new double[] { 0.0, 3.0 }, new double[] { 3.0, 4.0 }, new double[] { 4.0, 6.0 }, new double[] { 6.0, 10.0 });
			default -> Map.of();
		};
	}

	private Map<String, double[]> qualityIntervals(double[] level1, double[] level2, double[] level3, double[] level4, double[] level5) {
		Map<String, double[]> intervals = new LinkedHashMap<>();
		intervals.put("I", level1);
		intervals.put("II", level2);
		intervals.put("III", level3);
		intervals.put("IV", level4);
		intervals.put("V", level5);
		return intervals;
	}

	private Map<String, double[]> organicIntervals(double[] zero, double[] level12, double[] level3, double[] level4, double[] level5) {
		Map<String, double[]> intervals = new LinkedHashMap<>();
		intervals.put("0", zero);
		intervals.put("I/II", level12);
		intervals.put("III", level3);
		intervals.put("IV", level4);
		intervals.put("V", level5);
		return intervals;
	}

	private double randomBetween(double low, double high) {
		return Math.round(ThreadLocalRandom.current().nextDouble(low, high) * 100.0) / 100.0;
	}

	private String currentHourLabel() {
		return LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).format(TIME_FORMATTER);
	}

	private WaterQualityHistoryPoint toHistoryPoint(WaterQualityPoint point, String time) {
		return new WaterQualityHistoryPoint(
				time,
				point.nodeId(),
				point.ph(),
				point.dissolvedOxygen(),
				point.permanganateIndex(),
				point.ammoniaNitrogen(),
				point.totalPhosphorus(),
				point.chemicalOxygenDemand(),
				point.bod5(),
				point.level());
	}

	private WaterQualityOverview fallbackOverview() {
		Map<String, Integer> summary = emptySummary();
		List<WaterQualityPoint> nodes = new ArrayList<>();
		for (int nodeId = 1; nodeId <= 92; nodeId += 1) {
			String level = LEVELS.get(nodeId % LEVELS.size());
			summary.computeIfPresent(level, (key, count) -> count + 1);
			nodes.add(new WaterQualityPoint(
					nodeId,
					7.2,
					6.8,
					3.2,
					0.36,
					0.08,
					12.5,
					2.4,
					level));
		}
		return new WaterQualityOverview(
				"fallback",
				LocalDateTime.now().format(TIME_FORMATTER),
				false,
				summary,
				nodes);
	}
}
