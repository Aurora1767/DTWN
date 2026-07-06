package com.project.waternet.weather;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.project.waternet.weather.dto.RainfallHistoryPoint;
import com.project.waternet.weather.dto.RainfallOverview;

@Service
public class RainfallService {

	public RainfallOverview fetchHistory24h() {
		return fallbackOverview();
	}

	private RainfallOverview fallbackOverview() {
		int currentHour = LocalDateTime.now().getHour();
		List<RainfallHistoryPoint> points = new ArrayList<>();
		for (int index = 0; index < 24; index++) {
			int hour = (currentHour - 23 + index + 24) % 24;
			double upstream = round(Math.max(0, Math.sin(index / 3.0) * 2.4 + 1.1));
			double downstream = round(Math.max(0, Math.cos(index / 4.0) * 2.1 + 1.4));
			points.add(new RainfallHistoryPoint(
					String.format("%02d:00", hour),
					upstream,
					downstream,
					round((upstream + downstream) / 2.0)));
		}
		return new RainfallOverview(
				"fallback",
				LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
				true,
				points);
	}

	private double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
