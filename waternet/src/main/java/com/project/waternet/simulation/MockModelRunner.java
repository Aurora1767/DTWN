package com.project.waternet.simulation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.project.waternet.simulation.dto.SegmentParameter;
import com.project.waternet.simulation.dto.SegmentResult;
import com.project.waternet.simulation.dto.SimulationRequest;
import com.project.waternet.simulation.dto.SimulationResult;
import com.project.waternet.simulation.dto.TimeSeriesPoint;

@Component
public class MockModelRunner implements ModelRunner {

	@Override
	public SimulationResult run(SimulationRequest request) {
		LocalDateTime startedAt = LocalDateTime.now();
		List<SegmentParameter> segments = request.segments() == null || request.segments().isEmpty()
				? defaultSegments()
				: request.segments();
		int steps = request.steps() <= 0 ? 96 : Math.min(request.steps(), 288);
		int timeStep = request.timeStep() <= 0 ? 300 : request.timeStep();

		List<SegmentResult> results = segments.stream()
				.map(segment -> simulateSegment(segment, request, steps, timeStep))
				.toList();

		return new SimulationResult(
				"RUN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
				defaultText(request.scenarioName(), "Mock 防洪预演工况"),
				"SUCCESS",
				"MOCK",
				startedAt,
				LocalDateTime.now(),
				results);
	}

	private SegmentResult simulateSegment(SegmentParameter segment, SimulationRequest request, int steps, int timeStep) {
		double downstreamLevel = request.downstreamLevel() <= 0 ? 2.5 : request.downstreamLevel();
		double baseFlow = request.upstreamFlow() <= 0 ? 20.0 : request.upstreamFlow();
		double width = Math.max(segment.width(), 1.0);
		double n = segment.manningN() <= 0 ? 0.03 : segment.manningN();

		List<TimeSeriesPoint> series = java.util.stream.IntStream.range(0, steps)
				.mapToObj(step -> {
					double wave = Math.sin(step / 10.0);
					double flow = baseFlow * (1.0 + 0.16 * wave);
					double velocity = flow / Math.max(width * downstreamLevel, 0.1);
					double resistance = n * n * velocity * Math.abs(velocity) * 12.0;
					double waterLevel = downstreamLevel + 0.18 * wave + resistance + width / 1000.0;
					String riskLevel = waterLevel >= 2.72 ? "DANGER" : waterLevel >= 2.62 ? "WARNING" : "NORMAL";
					return new TimeSeriesPoint(
							step,
							step * timeStep,
							round(waterLevel),
							round(flow),
							round(velocity),
							riskLevel);
				})
				.toList();

		double maxWaterLevel = series.stream().mapToDouble(TimeSeriesPoint::waterLevel).max().orElse(0);
		double maxFlow = series.stream().mapToDouble(TimeSeriesPoint::flow).max().orElse(0);
		double averageVelocity = series.stream().mapToDouble(TimeSeriesPoint::velocity).average().orElse(0);

		return new SegmentResult(
				segment.code(),
				segment.code(),
				round(maxWaterLevel),
				round(maxFlow),
				round(averageVelocity),
				series);
	}

	private List<SegmentParameter> defaultSegments() {
		return List.of(
				new SegmentParameter("Lihe_S1", 1400, 20, 0.03),
				new SegmentParameter("Lihe_S2", 6400, 20, 0.03),
				new SegmentParameter("Jinghang_S1", 1500, 105, 0.03),
				new SegmentParameter("Jinghang_S2", 6100, 105, 0.02),
				new SegmentParameter("Jinghang_S3", 1500, 105, 0.02),
				new SegmentParameter("Daxigang", 5200, 30, 0.03));
	}

	private String defaultText(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private double round(double value) {
		return Math.round(value * 10000.0) / 10000.0;
	}
}
