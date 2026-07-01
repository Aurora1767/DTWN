package com.project.waternet.history;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.project.waternet.history.dto.HistoricalSensorRecord;

@Service
public class HistoryMockService {

	public List<HistoricalSensorRecord> sensorRecords(String nodeCode, LocalDateTime start, LocalDateTime end) {
		LocalDateTime base = LocalDateTime.now().minusHours(24).withMinute(0).withSecond(0).withNano(0);
		List<String> nodes = List.of("N_LH_TH", "N_JH_N", "N_JH_S", "N_DX_W");
		List<HistoricalSensorRecord> records = nodes.stream()
				.flatMap(node -> java.util.stream.IntStream.range(0, 25).mapToObj(index -> buildRecord(node, base.plusHours(index), index)))
				.filter(record -> nodeCode == null || nodeCode.isBlank() || record.nodeCode().equalsIgnoreCase(nodeCode))
				.filter(record -> start == null || !record.observedAt().isBefore(start))
				.filter(record -> end == null || !record.observedAt().isAfter(end))
				.sorted(Comparator.comparing(HistoricalSensorRecord::observedAt).reversed())
				.toList();
		return records;
	}

	public String toCsv(List<HistoricalSensorRecord> records) {
		StringBuilder builder = new StringBuilder("stationCode,stationName,nodeCode,observedAt,waterLevel,flow,velocity,rainfall,status\n");
		for (HistoricalSensorRecord record : records) {
			builder.append(record.stationCode()).append(',')
					.append(record.stationName()).append(',')
					.append(record.nodeCode()).append(',')
					.append(record.observedAt()).append(',')
					.append(record.waterLevel()).append(',')
					.append(record.flow()).append(',')
					.append(record.velocity()).append(',')
					.append(record.rainfall()).append(',')
					.append(record.status()).append('\n');
		}
		return builder.toString();
	}

	private HistoricalSensorRecord buildRecord(String nodeCode, LocalDateTime observedAt, int index) {
		double phase = index / 3.0;
		double baseLevel = switch (nodeCode) {
			case "N_JH_N" -> 2.53;
			case "N_JH_S" -> 2.45;
			case "N_DX_W" -> 2.42;
			default -> 2.50;
		};
		double waterLevel = round(baseLevel + Math.sin(phase) * 0.08);
		double flow = round(16.0 + Math.cos(phase) * 3.6 + nodeOffset(nodeCode));
		double velocity = round(flow / 62.0);
		double rainfall = round(Math.max(0, Math.sin(index / 4.0) * 2.2 + 1.2));
		String status = waterLevel >= 2.58 ? "WATCH" : "NORMAL";

		return new HistoricalSensorRecord(
				stationCode(nodeCode),
				stationName(nodeCode),
				nodeCode,
				observedAt,
				waterLevel,
				flow,
				velocity,
				rainfall,
				status);
	}

	private String stationCode(String nodeCode) {
		return switch (nodeCode) {
			case "N_JH_N" -> "ST_JH_N";
			case "N_JH_S" -> "ST_JH_S";
			case "N_DX_W" -> "ST_DX";
			default -> "ST_TH";
		};
	}

	private String stationName(String nodeCode) {
		return switch (nodeCode) {
			case "N_JH_N" -> "京杭运河北站";
			case "N_JH_S" -> "京杭运河南站";
			case "N_DX_W" -> "大溪港站";
			default -> "太湖边界站";
		};
	}

	private double nodeOffset(String nodeCode) {
		return switch (nodeCode) {
			case "N_JH_N" -> 2.8;
			case "N_JH_S" -> 1.6;
			case "N_DX_W" -> -1.4;
			default -> 0.0;
		};
	}

	private double round(double value) {
		return Math.round(value * 1000.0) / 1000.0;
	}
}
