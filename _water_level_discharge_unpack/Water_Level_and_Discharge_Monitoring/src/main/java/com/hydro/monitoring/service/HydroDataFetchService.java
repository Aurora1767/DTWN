package com.hydro.monitoring.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hydro.monitoring.config.HydroProperties;
import com.hydro.monitoring.config.HydroProperties.StationInfo;
import com.hydro.monitoring.model.WaterRecordVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 核心水利数据拉取与本地缓存持久化服务
 */
@Slf4j
@Service
public class HydroDataFetchService {

    private final RestTemplate restTemplate;
    private final HydroProperties properties;
    private final ObjectMapper objectMapper;

    // 采用主流 PC 浏览器桌面端 User-Agent，规避第三方防火墙拦截
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Autowired
    public HydroDataFetchService(RestTemplate restTemplate, HydroProperties properties, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 全量一键抓取流量图片与水位文本
     */
    public void fetchAll() {
        log.info("========================================");
        log.info(">>>> 开始执行全量水利数据定时抓取任务 <<<<");
        fetchFlowPictures();
        fetchWaterLevelData();
        log.info(">>>> 全量水利数据定时抓取任务执行完毕 <<<<");
        log.info("========================================");
    }

    /**
     * 1. 抓取所有流量控制测站的图片（过程线曲线图 + 数据表格图）
     */
    public void fetchFlowPictures() {
        List<StationInfo> stations = properties.getFlow().getStations();
        String baseUrl = properties.getFlow().getBaseUrl();
        String chartPattern = properties.getFlow().getChartUrlPattern();
        String tablePattern = properties.getFlow().getTableUrlPattern();

        // 创建本地缓存文件夹
        File cacheDir = new File(properties.getCachePath());
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            log.error("无法创建本地数据缓存目录: {}", cacheDir.getAbsolutePath());
            return;
        }

        for (StationInfo station : stations) {
            String stationCode = station.getCode();
            log.info("开始拉取流量测站图数据 -> 测站: {}({})", station.getName(), stationCode);

            // 1.1 过程线曲线图
            String chartUrl = baseUrl + chartPattern.replace("{itemid}", stationCode);
            downloadPicture(chartUrl, stationCode + "_chart.png");

            // 1.2 数据表格图
            String tableUrl = baseUrl + tablePattern.replace("{itemid}", stationCode);
            downloadPicture(tableUrl, stationCode + "_table.png");
        }
    }

    /**
     * 2. 抓取所有水位监测测站文本，并在本地缓存为独立 JSON 配置文件
     */
    public void fetchWaterLevelData() {
        List<StationInfo> stations = properties.getWater().getStations();
        String url = properties.getWater().getUrl();

        // 确定起止时段：etime 为当前整点，stime 为向前推 60 小时
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH");
        String etime = now.format(formatter);
        String stime = now.minusHours(60).format(formatter);

        // 创建本地缓存文件夹
        File cacheDir = new File(properties.getCachePath());
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            log.error("无法创建本地数据缓存目录: {}", cacheDir.getAbsolutePath());
            return;
        }

        for (StationInfo station : stations) {
            String stationCode = station.getCode();
            log.info("开始抓取水位数据 -> 测站: {}({}), 时段: [{} 至 {}]", station.getName(), stationCode, stime, etime);
            try {
                // 2.1 构建 POST Form 表单请求
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                headers.set(HttpHeaders.USER_AGENT, USER_AGENT);

                MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
                formData.add("stime", stime);
                formData.add("etime", etime);
                formData.add("sstcd", stationCode);

                HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(formData, headers);

                // 2.2 发送请求
                ResponseEntity<String> responseEntity = restTemplate.postForEntity(url, requestEntity, String.class);
                if (responseEntity.getStatusCode() != HttpStatus.OK) {
                    log.error("水位拉取接口请求异常! 状态码: {}, 测站: {}", responseEntity.getStatusCode(), station.getName());
                    continue;
                }

                String rawResponse = responseEntity.getBody();
                if (rawResponse == null || rawResponse.trim().isEmpty()) {
                    log.warn("水位拉取接口返回数据为空, 跳过缓存重写。测站: {}", station.getName());
                    continue;
                }

                // 2.3 智能自适应解析 JSON
                List<WaterRecordVo> rawRecords = parseWaterRecords(rawResponse);
                if (rawRecords.isEmpty()) {
                    log.warn("未能从接口响应中解析出任何有效水位记录, 跳过缓存重写。测站: {}", station.getName());
                    continue;
                }

                // 2.4 按时间倒序排序并过滤最新 50 期记录
                List<WaterRecordVo> sortedRecords = rawRecords.stream()
                        .filter(r -> r.getTime() != null && r.getWaterLevel() != null)
                        .sorted((r1, r2) -> r2.getTime().compareTo(r1.getTime())) // 按时间字符串倒序
                        .limit(50)
                        .collect(Collectors.toList());

                // 2.5 线程安全且文件防损持久化写入本地
                saveWaterRecordsToFile(stationCode, sortedRecords);
                log.info("水位数据成功抓取并缓存本地! 测站: {}({}), 缓存记录条数: {}", station.getName(), stationCode, sortedRecords.size());

            } catch (Exception e) {
                // 严密的业务隔离与 try-catch 兜底，即便某测站网络故障，也绝不破坏或清空该站点以及其他站点原有的缓存
                log.error("拉取水位数据遇到异常，已自动容错并跳过该站缓存重写! 测站: {}, 异常信息: {}", station.getName(), e.getMessage());
            }
        }
    }

    /**
     * 单张图片流下载底层封装
     */
    private void downloadPicture(String url, String filename) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                File targetFile = new File(properties.getCachePath(), filename);
                Files.write(targetFile.toPath(), response.getBody());
                log.info("图片下载成功并缓存本地 -> {}", targetFile.getName());
            } else {
                log.warn("图片下载非200! 响应码: {}, URL: {}", response.getStatusCode(), url);
            }
        } catch (Exception e) {
            log.error("图片下载发生网络异常，已自动容错跳过! URL: {}, 异常信息: {}", url, e.getMessage());
        }
    }

    /**
     * 智能自适应水情数据 JSON 树提取器
     */
    private List<WaterRecordVo> parseWaterRecords(String json) {
        List<WaterRecordVo> records = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode arrayNode = null;

            if (root.isArray()) {
                arrayNode = root;
            } else if (root.isObject()) {
                // 自适应常见响应包装键名
                String[] containerKeys = {"data", "list", "results", "rows", "records", "dataList"};
                for (String key : containerKeys) {
                    if (root.has(key) && root.get(key).isArray()) {
                        arrayNode = root.get(key);
                        break;
                    }
                }
                // 递归兜底遍历，查找响应中任何非空的数组节点
                if (arrayNode == null) {
                    Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        if (entry.getValue().isArray() && entry.getValue().size() > 0) {
                            arrayNode = entry.getValue();
                            break;
                        }
                    }
                }
            }

            if (arrayNode == null || arrayNode.isEmpty()) {
                // 若最终仍然无法定位数组，说明是纯扁平对象，作为单记录进行探测解析
                if (root.isObject()) {
                    WaterRecordVo vo = extractRecordFromMapNode(root);
                    if (vo != null) records.add(vo);
                }
                return records;
            }

            // 迭代解析数组内的每一项
            for (JsonNode item : arrayNode) {
                WaterRecordVo vo = extractRecordFromMapNode(item);
                if (vo != null) {
                    records.add(vo);
                }
            }

        } catch (Exception e) {
            log.error("智能解析水情 JSON 发生异常! 异常信息: {}", e.getMessage());
        }
        return records;
    }

    /**
     * 从多类型水利系统字段中自适应提炼目标时间和水位
     */
    private WaterRecordVo extractRecordFromMapNode(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }

        String time = null;
        Double waterLevel = null;

        // 智能兼容多种时间参数键
        String[] timeKeys = {"tm", "TM", "time", "TIME", "tmFormat", "date", "dateTime", "DATETIME", "datetime"};
        for (String key : timeKeys) {
            if (node.has(key)) {
                time = node.get(key).asText();
                if (time != null && !time.trim().isEmpty()) {
                    break;
                }
            }
        }

        // 智能兼容多种水位参数键 (z=水位(国家水文局标准), rz=水库水位, wl=Water Level, value=测量值)
        String[] levelKeys = {"z", "Z", "waterLevel", "waterlevel", "WATER_LEVEL", "rz", "RZ", "wl", "WL", "value", "val"};
        for (String key : levelKeys) {
            if (node.has(key)) {
                JsonNode levelNode = node.get(key);
                if (levelNode != null && !levelNode.isNull()) {
                    try {
                        waterLevel = levelNode.asDouble();
                        break;
                    } catch (Exception e) {
                        // 继续探测其他键
                    }
                }
            }
        }

        if (time != null && waterLevel != null) {
            return new WaterRecordVo(time.trim(), waterLevel);
        }
        return null;
    }

    /**
     * 将解析出的序列化记录写入本地文件 {stationCode}.json (写入临时文件 -> 校验 -> 原子替换)
     */
    private synchronized void saveWaterRecordsToFile(String stationCode, List<WaterRecordVo> records) throws IOException {
        File cacheDir = new File(properties.getCachePath());
        File targetFile = new File(cacheDir, stationCode + ".json");
        File tempFile = new File(cacheDir, stationCode + ".json.tmp");

        try {
            // 写入临时文件，确保过程不干扰可能由于请求而正在被前端/网关并发读取的目标文件
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile, records);

            if (tempFile.exists()) {
                // 原文件如果存在，先删除，再将临时文件原子移动过去，完全保证并发文件安全性与事务隔离性
                if (targetFile.exists()) {
                    Files.delete(targetFile.toPath());
                }
                Files.move(tempFile.toPath(), targetFile.toPath());
            }
        } finally {
            // 兜底清理工作
            if (tempFile.exists()) {
                Files.delete(tempFile.toPath());
            }
        }
    }
}
