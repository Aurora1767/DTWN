package com.project.waternet.network;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import com.project.waternet.common.ApiResponse;

@RestController
@RequestMapping("/api/database")
public class DatabaseCrudController {

	private final Path databasePath;
	private final Path fallbackDatabasePath;

	public DatabaseCrudController(@Value("${waternet.database.path:data/waternet.db}") String databasePath) {
		this.databasePath = Path.of(databasePath).toAbsolutePath().normalize();
		this.fallbackDatabasePath = Path.of("waternet").resolve(databasePath).toAbsolutePath().normalize();
	}

	@GetMapping("/tables")
	public ApiResponse<List<String>> tables() {
		try (Connection conn = connect()) {
			List<String> tables = new ArrayList<>();
			ResultSet rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"});
			while (rs.next()) {
				String name = rs.getString("TABLE_NAME");
				if (!"users".equalsIgnoreCase(name)) {
					tables.add(name);
				}
			}
			return ApiResponse.ok(tables);
		} catch (SQLException e) {
			return ApiResponse.error(500, e.getMessage());
		}
	}

	@GetMapping("/tables/{table}/schema")
	public ApiResponse<List<Map<String, Object>>> tableSchema(@PathVariable String table) {
		try (Connection conn = connect()) {
			validateTable(conn, table);
			List<Map<String, Object>> columns = new ArrayList<>();
			try (ResultSet rs = conn.createStatement().executeQuery("PRAGMA table_info(\"" + table + "\")")) {
				while (rs.next()) {
					Map<String, Object> col = new LinkedHashMap<>();
					col.put("cid", rs.getInt("cid"));
					col.put("name", rs.getString("name"));
					col.put("type", rs.getString("type"));
					col.put("notnull", rs.getInt("notnull"));
					col.put("pk", rs.getInt("pk"));
					columns.add(col);
				}
			}
			return ApiResponse.ok(columns);
		} catch (SQLException e) {
			return ApiResponse.error(500, e.getMessage());
		}
	}

	@GetMapping("/tables/{table}/rows")
	public ApiResponse<Map<String, Object>> queryRows(
			@PathVariable String table,
			@RequestParam(defaultValue = "100") int limit,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(required = false) String search) {
		try (Connection conn = connect()) {
			validateTable(conn, table);
			String baseQuery = "SELECT * FROM \"" + table + "\"";
			String countQuery = "SELECT count(*) FROM \"" + table + "\"";
			List<Object> params = new ArrayList<>();

			if (search != null && !search.isBlank()) {
				List<String> colNames = getColumnNames(conn, table);
				StringBuilder where = new StringBuilder(" WHERE ");
				for (int i = 0; i < colNames.size(); i++) {
					if (i > 0) where.append(" OR ");
					where.append("CAST(\"").append(colNames.get(i)).append("\" AS TEXT) LIKE ?");
					params.add("%" + search.strip() + "%");
				}
				baseQuery += where;
				countQuery += where;
			}

			int total;
			try (PreparedStatement ps = conn.prepareStatement(countQuery)) {
				for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
				ResultSet rs = ps.executeQuery();
				rs.next();
				total = rs.getInt(1);
			}

			baseQuery += " LIMIT ? OFFSET ?";
			List<Map<String, Object>> rows = new ArrayList<>();
			try (PreparedStatement ps = conn.prepareStatement(baseQuery)) {
				for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
				ps.setInt(params.size() + 1, limit);
				ps.setInt(params.size() + 2, offset);
				ResultSet rs = ps.executeQuery();
				ResultSetMetaData meta = rs.getMetaData();
				int colCount = meta.getColumnCount();
				while (rs.next()) {
					Map<String, Object> row = new LinkedHashMap<>();
					for (int c = 1; c <= colCount; c++) {
						row.put(meta.getColumnName(c), rs.getObject(c));
					}
					rows.add(row);
				}
			}

			Map<String, Object> result = new LinkedHashMap<>();
			result.put("total", total);
			result.put("rows", rows);
			return ApiResponse.ok(result);
		} catch (SQLException e) {
			return ApiResponse.error(500, e.getMessage());
		}
	}

	@PostMapping("/tables/{table}/rows")
	public ApiResponse<String> insertRow(@PathVariable String table, @RequestBody Map<String, Object> row) {
		try (Connection conn = connect()) {
			validateTable(conn, table);
			if (row.isEmpty()) return ApiResponse.error(400, "Empty row");
			List<String> cols = new ArrayList<>(row.keySet());
			String placeholders = String.join(", ", Collections.nCopies(cols.size(), "?"));
			String colList = cols.stream().map(c -> "\"" + c + "\"").reduce((a, b) -> a + ", " + b).orElse("");
			String sql = "INSERT INTO \"" + table + "\" (" + colList + ") VALUES (" + placeholders + ")";
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				for (int i = 0; i < cols.size(); i++) ps.setObject(i + 1, row.get(cols.get(i)));
				ps.executeUpdate();
			}
			return ApiResponse.ok("inserted");
		} catch (SQLException e) {
			return ApiResponse.error(500, e.getMessage());
		}
	}

	@PutMapping("/tables/{table}/rows")
	public ApiResponse<String> updateRow(
			@PathVariable String table,
			@RequestBody Map<String, Object> body) {
		try (Connection conn = connect()) {
			validateTable(conn, table);
			@SuppressWarnings("unchecked")
			Map<String, Object> keys = (Map<String, Object>) body.get("_keys");
			@SuppressWarnings("unchecked")
			Map<String, Object> values = (Map<String, Object>) body.get("_values");
			if (keys == null || keys.isEmpty() || values == null || values.isEmpty())
				return ApiResponse.error(400, "Missing _keys or _values");

			StringBuilder sql = new StringBuilder("UPDATE \"" + table + "\" SET ");
			List<Object> params = new ArrayList<>();
			int idx = 0;
			for (Map.Entry<String, Object> entry : values.entrySet()) {
				if (idx > 0) sql.append(", ");
				sql.append("\"").append(entry.getKey()).append("\" = ?");
				params.add(entry.getValue());
				idx++;
			}
			sql.append(" WHERE ");
			idx = 0;
			for (Map.Entry<String, Object> entry : keys.entrySet()) {
				if (idx > 0) sql.append(" AND ");
				sql.append("\"").append(entry.getKey()).append("\" = ?");
				params.add(entry.getValue());
				idx++;
			}
			try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
				for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
				int affected = ps.executeUpdate();
				return ApiResponse.ok("updated " + affected + " row(s)");
			}
		} catch (SQLException e) {
			return ApiResponse.error(500, e.getMessage());
		}
	}

	@DeleteMapping("/tables/{table}/rows")
	public ApiResponse<String> deleteRow(
			@PathVariable String table,
			@RequestBody Map<String, Object> keys) {
		try (Connection conn = connect()) {
			validateTable(conn, table);
			if (keys.isEmpty()) return ApiResponse.error(400, "Missing keys");
			StringBuilder sql = new StringBuilder("DELETE FROM \"" + table + "\" WHERE ");
			List<Object> params = new ArrayList<>();
			int idx = 0;
			for (Map.Entry<String, Object> entry : keys.entrySet()) {
				if (idx > 0) sql.append(" AND ");
				sql.append("\"").append(entry.getKey()).append("\" = ?");
				params.add(entry.getValue());
				idx++;
			}
			try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
				for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
				int affected = ps.executeUpdate();
				return ApiResponse.ok("deleted " + affected + " row(s)");
			}
		} catch (SQLException e) {
			return ApiResponse.error(500, e.getMessage());
		}
	}

	private List<String> getColumnNames(Connection conn, String table) throws SQLException {
		List<String> cols = new ArrayList<>();
		try (ResultSet rs = conn.createStatement().executeQuery("PRAGMA table_info(\"" + table + "\")")) {
			while (rs.next()) cols.add(rs.getString("name"));
		}
		return cols;
	}

	private Connection connect() throws SQLException {
		Path resolved = resolvedDatabasePath();
		return DriverManager.getConnection("jdbc:sqlite:" + resolved);
	}

	private Path resolvedDatabasePath() throws SQLException {
		if (Files.isRegularFile(databasePath)) return databasePath;
		if (!fallbackDatabasePath.equals(databasePath) && Files.isRegularFile(fallbackDatabasePath))
			return fallbackDatabasePath;
		throw new SQLException("Database file not found");
	}

	private void validateTable(Connection conn, String table) throws SQLException {
		ResultSet rs = conn.getMetaData().getTables(null, null, table, new String[]{"TABLE"});
		if (!rs.next()) throw new SQLException("Table not found: " + table);
		if ("users".equalsIgnoreCase(table)) throw new SQLException("Access denied");
	}
}
