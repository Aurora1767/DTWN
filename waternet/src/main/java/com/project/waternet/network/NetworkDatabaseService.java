package com.project.waternet.network;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.waternet.network.dto.NodeHydrologySeries;
import com.project.waternet.network.dto.NodeHydrologySeries.NodeHydrologyPoint;
import com.project.waternet.network.dto.RiverSegment;
import com.project.waternet.network.dto.RiverSegment.Coordinate;
import com.project.waternet.network.dto.RiverSegment.SegmentHydrologyStats;
import com.project.waternet.network.dto.SegmentProfile;
import com.project.waternet.network.dto.SegmentProfile.SegmentProfilePoint;
import com.project.waternet.network.dto.WaterNode;
import com.project.waternet.network.dto.WaterNode.NodeLatestHydrology;

@Service
public class NetworkDatabaseService {

	private static final TypeReference<List<Coordinate>> COORDINATES_TYPE = new TypeReference<>() {
	};
	private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
	};
	private static final TypeReference<List<Integer>> INTEGER_LIST_TYPE = new TypeReference<>() {
	};

	private final Path databasePath;
	private final Path fallbackDatabasePath;
	private final ObjectMapper objectMapper;

	public NetworkDatabaseService(@Value("${waternet.database.path:data/waternet.db}") String databasePath) {
		this.databasePath = Path.of(databasePath).toAbsolutePath().normalize();
		this.fallbackDatabasePath = Path.of("waternet").resolve(databasePath).toAbsolutePath().normalize();
		this.objectMapper = new ObjectMapper()
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	public Optional<Map<String, Object>> overview() {
		Optional<Path> resolvedPath = resolvedDatabasePath();
		if (resolvedPath.isEmpty()) {
			return Optional.empty();
		}

		try (Connection connection = connect(resolvedPath.get())) {
			return Optional.of(Map.of(
					"segments", segments(connection),
					"nodes", nodes(connection),
					"structures", List.of(),
					"source", "sqlite",
					"updatedAt", Instant.now().toString()));
		} catch (SQLException exception) {
			return Optional.empty();
		}
	}

	public Optional<List<RiverSegment>> segments() {
		Optional<Path> resolvedPath = resolvedDatabasePath();
		if (resolvedPath.isEmpty()) {
			return Optional.empty();
		}
		try (Connection connection = connect(resolvedPath.get())) {
			return Optional.of(segments(connection));
		} catch (SQLException exception) {
			return Optional.empty();
		}
	}

	public Optional<List<WaterNode>> nodes() {
		Optional<Path> resolvedPath = resolvedDatabasePath();
		if (resolvedPath.isEmpty()) {
			return Optional.empty();
		}
		try (Connection connection = connect(resolvedPath.get())) {
			return Optional.of(nodes(connection));
		} catch (SQLException exception) {
			return Optional.empty();
		}
	}

	public Optional<SegmentProfile> segmentProfile(String segmentCode) {
		Optional<Path> resolvedPath = resolvedDatabasePath();
		if (resolvedPath.isEmpty()) {
			return Optional.empty();
		}
		try (Connection connection = connect(resolvedPath.get())) {
			return segmentProfile(connection, segmentCode);
		} catch (SQLException exception) {
			return Optional.empty();
		}
	}

	public Optional<NodeHydrologySeries> nodeSeries(String nodeCode, int recentHours) {
		Optional<Path> resolvedPath = resolvedDatabasePath();
		if (resolvedPath.isEmpty()) {
			return Optional.empty();
		}
		try (Connection connection = connect(resolvedPath.get())) {
			return nodeSeries(connection, nodeCode, recentHours);
		} catch (SQLException exception) {
			return Optional.empty();
		}
	}

	private Optional<Path> resolvedDatabasePath() {
		if (Files.isRegularFile(databasePath)) {
			return Optional.of(databasePath);
		}
		if (!fallbackDatabasePath.equals(databasePath) && Files.isRegularFile(fallbackDatabasePath)) {
			return Optional.of(fallbackDatabasePath);
		}
		return Optional.empty();
	}

	private Connection connect(Path resolvedPath) throws SQLException {
		return DriverManager.getConnection("jdbc:sqlite:" + resolvedPath);
	}

	private List<RiverSegment> segments(Connection connection) throws SQLException {
		String sql = """
				select
				  s.code,
				  s.name,
				  s.reach_id,
				  s.start_node_code,
				  s.end_node_code,
				  s.length_meters,
				  s.width_meters,
				  s.dx,
				  s.chezy,
				  s.bed_elevation,
				  s.coordinates_json,
				  h.max_flow,
				  h.min_flow,
				  h.max_water_level,
				  h.min_water_level,
				  h.profile_hour,
				  h.sample_count
				from river_segments s
				left join segment_hydrology_stats h on h.segment_code = s.code
				order by s.reach_id
				""";
		List<RiverSegment> segments = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement(sql);
				ResultSet resultSet = statement.executeQuery()) {
			while (resultSet.next()) {
				segments.add(new RiverSegment(
						resultSet.getString("code"),
						resultSet.getString("name"),
						getInteger(resultSet, "reach_id"),
						resultSet.getDouble("length_meters"),
						resultSet.getDouble("width_meters"),
						0.03,
						getDouble(resultSet, "chezy"),
						getDouble(resultSet, "dx"),
						getDouble(resultSet, "bed_elevation"),
						resultSet.getString("start_node_code"),
						resultSet.getString("end_node_code"),
						readJson(resultSet.getString("coordinates_json"), COORDINATES_TYPE, List.of()),
						new SegmentHydrologyStats(
								getDouble(resultSet, "max_flow"),
								getDouble(resultSet, "min_flow"),
								getDouble(resultSet, "max_water_level"),
								getDouble(resultSet, "min_water_level"),
								getInteger(resultSet, "profile_hour"),
								getInteger(resultSet, "sample_count"))));
			}
		}
		return segments;
	}

	private List<WaterNode> nodes(Connection connection) throws SQLException {
		String sql = """
				select
				  n.code,
				  n.name,
				  n.node_type,
				  n.lng,
				  n.lat,
				  n.initial_water_level,
				  n.boundary_type,
				  n.connected_node_codes_json,
				  n.connected_segment_codes_json,
				  n.connected_reach_ids_json,
				  h.hour,
				  h.water_level,
				  h.flow
				from water_nodes n
				left join node_latest_hydrology h on h.node_code = n.code
				order by n.node_id
				""";
		List<WaterNode> nodes = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement(sql);
				ResultSet resultSet = statement.executeQuery()) {
			while (resultSet.next()) {
				nodes.add(new WaterNode(
						resultSet.getString("code"),
						resultSet.getString("name"),
						resultSet.getString("node_type"),
						resultSet.getDouble("lng"),
						resultSet.getDouble("lat"),
						resultSet.getDouble("initial_water_level"),
						resultSet.getString("boundary_type"),
						readJson(resultSet.getString("connected_node_codes_json"), STRING_LIST_TYPE, List.of()),
						readJson(resultSet.getString("connected_segment_codes_json"), STRING_LIST_TYPE, List.of()),
						readJson(resultSet.getString("connected_reach_ids_json"), INTEGER_LIST_TYPE, List.of()),
						new NodeLatestHydrology(
								getInteger(resultSet, "hour"),
								getDouble(resultSet, "water_level"),
								getDouble(resultSet, "flow"))));
			}
		}
		return nodes;
	}

	private Optional<SegmentProfile> segmentProfile(Connection connection, String segmentCode) throws SQLException {
		String sql = """
				select
				  p.segment_code,
				  p.reach_id,
				  p.start_node_code,
				  p.end_node_code,
				  p.section_no,
				  p.x_m,
				  p.water_level,
				  p.flow,
				  h.profile_hour
				from segment_profile_results p
				left join segment_hydrology_stats h on h.segment_code = p.segment_code
				where p.segment_code = ?
				order by p.section_no
				""";
		List<SegmentProfilePoint> points = new ArrayList<>();
		Integer reachId = null;
		String startNode = null;
		String endNode = null;
		Integer profileHour = null;
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, segmentCode);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					reachId = getInteger(resultSet, "reach_id");
					startNode = resultSet.getString("start_node_code");
					endNode = resultSet.getString("end_node_code");
					profileHour = getInteger(resultSet, "profile_hour");
					points.add(new SegmentProfilePoint(
							resultSet.getInt("section_no"),
							resultSet.getDouble("x_m"),
							getDouble(resultSet, "water_level"),
							getDouble(resultSet, "flow")));
				}
			}
		}
		if (points.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new SegmentProfile(segmentCode, reachId, startNode, endNode, profileHour, points));
	}

	private Optional<NodeHydrologySeries> nodeSeries(Connection connection, String nodeCode, int recentHours)
			throws SQLException {
		String maxHourSql = "select max(hour) as max_hour from node_hydrology_timeseries where node_code = ?";
		Integer maxHour = null;
		try (PreparedStatement statement = connection.prepareStatement(maxHourSql)) {
			statement.setString(1, nodeCode);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (resultSet.next()) {
					maxHour = getInteger(resultSet, "max_hour");
				}
			}
		}
		if (maxHour == null) {
			return Optional.empty();
		}
		int fromHour = recentHours > 0 ? Math.max(0, maxHour - recentHours + 1) : 0;

		String sql = """
				select hour, water_level, flow
				from node_hydrology_timeseries
				where node_code = ? and hour >= ?
				order by hour
				""";
		List<NodeHydrologyPoint> points = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, nodeCode);
			statement.setInt(2, fromHour);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					points.add(new NodeHydrologyPoint(
							resultSet.getInt("hour"),
							getDouble(resultSet, "water_level"),
							getDouble(resultSet, "flow")));
				}
			}
		}
		if (points.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new NodeHydrologySeries(nodeCode, points));
	}

	private <T> T readJson(String value, TypeReference<T> typeReference, T fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}
		try {
			return objectMapper.readValue(value, typeReference);
		} catch (Exception exception) {
			return fallback;
		}
	}

	private Double getDouble(ResultSet resultSet, String column) throws SQLException {
		double value = resultSet.getDouble(column);
		return resultSet.wasNull() ? null : value;
	}

	private Integer getInteger(ResultSet resultSet, String column) throws SQLException {
		int value = resultSet.getInt(column);
		return resultSet.wasNull() ? null : value;
	}
}
