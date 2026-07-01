package com.project.waternet.history;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.waternet.common.ApiResponse;
import com.project.waternet.history.dto.HistoricalSensorRecord;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

	private final HistoryMockService historyMockService;

	public HistoryController(HistoryMockService historyMockService) {
		this.historyMockService = historyMockService;
	}

	@GetMapping("/sensor-records")
	public ApiResponse<List<HistoricalSensorRecord>> sensorRecords(
			@RequestParam(required = false) String nodeCode,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
		return ApiResponse.ok(historyMockService.sensorRecords(nodeCode, start, end));
	}

	@GetMapping(value = "/sensor-records/export", produces = "text/csv")
	public ResponseEntity<byte[]> exportSensorRecords(
			@RequestParam(required = false) String nodeCode,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
		String csv = historyMockService.toCsv(historyMockService.sensorRecords(nodeCode, start, end));
		byte[] bytes = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename("sensor-records.csv", StandardCharsets.UTF_8).build().toString())
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.body(bytes);
	}
}
