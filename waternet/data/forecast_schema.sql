-- 数字孪生水网 · 河网预报记录数据库结构 (SQLite)
-- 数据库文件默认路径: waternet/data/waternet-forecast.db
-- 应用启动时会自动创建表结构；也可使用 sqlite3 手动初始化本脚本。

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS forecast_record (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  calculated_at TEXT NOT NULL,
  forecast_hours REAL NOT NULL,
  result_json TEXT NOT NULL,
  created_at TEXT NOT NULL,
  record_type TEXT NOT NULL DEFAULT 'forecast'
);

CREATE INDEX IF NOT EXISTS idx_forecast_record_calculated_at
  ON forecast_record (calculated_at DESC);

CREATE INDEX IF NOT EXISTS idx_forecast_record_type
  ON forecast_record (record_type, id DESC);

-- result_json 字段保存完整 RiverNetworkForecastResult JSON，包含：
-- forecastHours, nSteps, dt, nodeHistories, reaches, reachProfiles 等。
