package com.project.waternet.config;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import org.sqlite.SQLiteDataSource;

@Configuration
public class ForecastDatabaseConfig {

	@Bean(name = "forecastDataSource")
	public DataSource forecastDataSource(
			@Value("${waternet.forecast-db.path:data/waternet-forecast.db}") String databasePath) throws Exception {
		Path dbFile = Path.of(databasePath);
		if (!dbFile.isAbsolute()) {
			dbFile = Path.of(System.getProperty("user.dir")).resolve(dbFile);
		}
		Path dataDir = dbFile.getParent();
		if (dataDir != null) {
			Files.createDirectories(dataDir);
		}

		SQLiteDataSource dataSource = new SQLiteDataSource();
		dataSource.setUrl("jdbc:sqlite:" + dbFile.toAbsolutePath());
		try (var connection = dataSource.getConnection();
				var statement = connection.createStatement()) {
			statement.execute("PRAGMA journal_mode=WAL");
			statement.execute("PRAGMA synchronous=NORMAL");
			statement.execute("PRAGMA busy_timeout=5000");
		}
		return dataSource;
	}

	@Bean(name = "forecastJdbcTemplate")
	public JdbcTemplate forecastJdbcTemplate(@Qualifier("forecastDataSource") DataSource forecastDataSource) {
		return new JdbcTemplate(forecastDataSource);
	}
}
