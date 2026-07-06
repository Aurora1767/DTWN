package com.project.waternet.waterquality;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.project.waternet.waterquality.dto.WaterQualityHistoryPoint;
import com.project.waternet.waterquality.dto.WaterQualityPoint;

import jakarta.annotation.PostConstruct;

@Repository
public class WaterQualityRepository {

	private final JdbcTemplate jdbcTemplate;

	public WaterQualityRepository(@Qualifier("waterQualityJdbcTemplate") JdbcTemplate waterQualityJdbcTemplate) {
		this.jdbcTemplate = waterQualityJdbcTemplate;
	}

	@PostConstruct
	public void initSchema() {
		jdbcTemplate.execute("""
				CREATE TABLE IF NOT EXISTS quality (
				  idwater_quality INTEGER PRIMARY KEY AUTOINCREMENT,
				  id TEXT NOT NULL,
				  time TEXT NOT NULL,
				  pH TEXT,
				  DO TEXT,
				  Mn TEXT,
				  "NH3-N" TEXT,
				  TP TEXT,
				  COD TEXT,
				  BOD TEXT,
				  water_level TEXT,
				  UNIQUE(id, time)
				)
				""");
		jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_quality_time ON quality (time)");
		jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_quality_id_time ON quality (id, time)");
	}

	public int countAt(String time) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM quality WHERE time = ?",
				Integer.class,
				time);
		return count == null ? 0 : count;
	}

	public void upsert(String time, List<WaterQualityPoint> points) {
		jdbcTemplate.batchUpdate(
				"""
						INSERT INTO quality (id, time, pH, DO, Mn, "NH3-N", TP, COD, BOD, water_level)
						VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						ON CONFLICT(id, time) DO UPDATE SET
						  pH = excluded.pH,
						  DO = excluded.DO,
						  Mn = excluded.Mn,
						  "NH3-N" = excluded."NH3-N",
						  TP = excluded.TP,
						  COD = excluded.COD,
						  BOD = excluded.BOD,
						  water_level = excluded.water_level
						""",
				points,
				points.size(),
				(ps, point) -> {
					ps.setString(1, String.valueOf(point.nodeId()));
					ps.setString(2, time);
					ps.setString(3, String.valueOf(point.ph()));
					ps.setString(4, String.valueOf(point.dissolvedOxygen()));
					ps.setString(5, String.valueOf(point.permanganateIndex()));
					ps.setString(6, String.valueOf(point.ammoniaNitrogen()));
					ps.setString(7, String.valueOf(point.totalPhosphorus()));
					ps.setString(8, String.valueOf(point.chemicalOxygenDemand()));
					ps.setString(9, String.valueOf(point.bod5()));
					ps.setString(10, point.level());
				});
	}

	public List<WaterQualityHistoryPoint> findHistory(int nodeId, int hours) {
		int limit = Math.max(1, Math.min(hours, 168));
		List<WaterQualityHistoryPoint> latestFirst = jdbcTemplate.query(
				"""
						SELECT id, time, pH, DO, Mn, "NH3-N", TP, COD, BOD, water_level
						FROM quality
						WHERE id = ?
						ORDER BY time DESC
						LIMIT ?
						""",
				this::mapHistoryPoint,
				String.valueOf(nodeId),
				limit);
		List<WaterQualityHistoryPoint> chronological = new ArrayList<>(latestFirst);
		Collections.reverse(chronological);
		return chronological;
	}

	private WaterQualityHistoryPoint mapHistoryPoint(ResultSet rs, int rowNum) throws SQLException {
		return new WaterQualityHistoryPoint(
				rs.getString("time"),
				Integer.parseInt(rs.getString("id")),
				readDouble(rs, "pH"),
				readDouble(rs, "DO"),
				readDouble(rs, "Mn"),
				readDouble(rs, "NH3-N"),
				readDouble(rs, "TP"),
				readDouble(rs, "COD"),
				readDouble(rs, "BOD"),
				rs.getString("water_level"));
	}

	private double readDouble(ResultSet rs, String column) throws SQLException {
		String value = rs.getString(column);
		if (value == null || value.isBlank()) {
			return 0.0;
		}
		return Double.parseDouble(value);
	}
}
