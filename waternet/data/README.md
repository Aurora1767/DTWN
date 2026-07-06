# 预报记录数据库

## 数据库文件

- 路径：`waternet/data/waternet-forecast.db`
- 类型：SQLite
- 首次启动后端时自动创建

## 表结构

见同目录 `forecast_schema.sql`。

## 手动查看数据

```bash
sqlite3 data/waternet-forecast.db
.tables
SELECT id, calculated_at, forecast_hours FROM forecast_record ORDER BY id DESC;
```
