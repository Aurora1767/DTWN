package com.project.waternet.rivernetwork;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.project.waternet.rivernetwork.dto.ForecastRecordSummary;

import jakarta.annotation.PostConstruct;

@Repository
public class ForecastRecordRepository {

	private static final RowMapper<ForecastRecordSummary> SUMMARY_MAPPER = (rs, rowNum) -> new ForecastRecordSummary(
			rs.getLong("id"),
			rs.getString("calculated_at"),
			rs.getDouble("forecast_hours"),
			rs.getString("record_type"),
			readOptionalString(rs, "simulation_name"));

	private final JdbcTemplate jdbcTemplate;

	public ForecastRecordRepository(@Qualifier("forecastJdbcTemplate") JdbcTemplate forecastJdbcTemplate) {
		this.jdbcTemplate = forecastJdbcTemplate;
	}

	@PostConstruct
	public void initSchema() {
		jdbcTemplate.execute("""
				CREATE TABLE IF NOT EXISTS forecast_record (
				  id INTEGER PRIMARY KEY AUTOINCREMENT,
				  calculated_at TEXT NOT NULL,
				  forecast_hours REAL NOT NULL,
				  result_json TEXT NOT NULL,
				  created_at TEXT NOT NULL,
				  record_type TEXT NOT NULL DEFAULT 'forecast'
				)
				""");
		try {
			jdbcTemplate.execute("""
					ALTER TABLE forecast_record
					ADD COLUMN record_type TEXT NOT NULL DEFAULT 'forecast'
					""");
		} catch (Exception ignored) {
			// column already exists
		}
		try {
			jdbcTemplate.execute("""
					ALTER TABLE forecast_record
					ADD COLUMN simulation_name TEXT
					""");
		} catch (Exception ignored) {
			// column already exists
		}
		try {
			jdbcTemplate.execute("""
					ALTER TABLE forecast_record
					ADD COLUMN settings_json TEXT
					""");
		} catch (Exception ignored) {
			// column already exists
		}
		jdbcTemplate.execute("""
				CREATE INDEX IF NOT EXISTS idx_forecast_record_calculated_at
				ON forecast_record (calculated_at DESC)
				""");
		jdbcTemplate.execute("""
				CREATE INDEX IF NOT EXISTS idx_forecast_record_type
				ON forecast_record (record_type, id DESC)
				""");
	}

	public long insert(
			String calculatedAt,
			double forecastHours,
			String resultJson,
			String recordType,
			String simulationName,
			String settingsJson) {
		String createdAt = Instant.now().toString();
		jdbcTemplate.update(
				"""
						INSERT INTO forecast_record
						(calculated_at, forecast_hours, result_json, created_at, record_type, simulation_name, settings_json)
						VALUES (?, ?, ?, ?, ?, ?, ?)
						""",
				calculatedAt,
				forecastHours,
				resultJson,
				createdAt,
				recordType,
				simulationName,
				settingsJson);
		Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		if (id == null) {
			throw new IllegalStateException("Failed to retrieve inserted forecast record id");
		}
		return id;
	}

	public List<ForecastRecordSummary> findSummariesByType(String recordType) {
		return jdbcTemplate.query(
				"""
						SELECT id, calculated_at, forecast_hours, record_type, simulation_name
						FROM forecast_record
						WHERE record_type = ?
						ORDER BY id DESC
						""",
				SUMMARY_MAPPER,
				recordType);
	}

	public Optional<StoredForecastRecord> findById(long id) {
		List<StoredForecastRecord> records = jdbcTemplate.query(
				"""
						SELECT id, calculated_at, forecast_hours, result_json, record_type, simulation_name, settings_json
						FROM forecast_record
						WHERE id = ?
						""",
				this::mapStoredRecord,
				id);
		return records.stream().findFirst();
	}

	public boolean deleteById(long id) {
		return jdbcTemplate.update("DELETE FROM forecast_record WHERE id = ?", id) > 0;
	}

	private StoredForecastRecord mapStoredRecord(ResultSet rs, int rowNum) throws SQLException {
		return new StoredForecastRecord(
				rs.getLong("id"),
				rs.getString("calculated_at"),
				rs.getDouble("forecast_hours"),
				rs.getString("result_json"),
				rs.getString("record_type"),
				readOptionalString(rs, "simulation_name"),
				readOptionalString(rs, "settings_json"));
	}

	private static String readOptionalString(ResultSet rs, String column) throws SQLException {
		String value = rs.getString(column);
		return value == null || value.isBlank() ? null : value;
	}

	public record StoredForecastRecord(
			long id,
			String calculatedAt,
			double forecastHours,
			String resultJson,
			String recordType,
			String simulationName,
			String settingsJson) {
	}
}
