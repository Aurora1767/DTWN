package com.hydro.monitoring.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 水利抓取配置属性与防腐/缓存映射层
 */
@Data
@Component
@ConfigurationProperties(prefix = "hydro")
public class HydroProperties {

    /**
     * 本地缓存根路径
     */
    private String cachePath = "upload/hydro_data/";

    /**
     * 流量测站相关配置
     */
    private FlowConfig flow = new FlowConfig();

    /**
     * 水位测站相关配置
     */
    private WaterConfig water = new WaterConfig();

    // 辅助运行时字段，便于根据 stationKey 极速定位测站信息
    private final Map<String, StationInfo> flowStationMap = new HashMap<>();
    private final Map<String, StationInfo> waterStationMap = new HashMap<>();

    @PostConstruct
    public void init() {
        // 如果 application.yml 未配，则自动采用内置标准业务数据兜底，确保开箱即用
        if (flow.getStations().isEmpty()) {
            flow.getStations().add(new StationInfo("zhihu", "直湖港闸", "700de6cf4fce4b158bc1ad5c34d7229e"));
            flow.getStations().add(new StationInfo("wangting_flow", "望亭立交流量", "33d10b545cc94711a2ce7797960ec2d87"));
            flow.getStations().add(new StationInfo("yixing", "宜兴南流量", "fd02f5801b7a4e88ab5741d609e89d15"));
        }
        if (water.getStations().isEmpty()) {
            water.getStations().add(new StationInfo("luoshe", "洛社", "63202800"));
            water.getStations().add(new StationInfo("wuxi", "无锡大", "63202851"));
            water.getStations().add(new StationInfo("wangting_tai", "望亭太", "63201300"));
            water.getStations().add(new StationInfo("wangting_lijiao", "望亭立交水位", "63202911"));
            water.getStations().add(new StationInfo("wangting_da", "望亭大水位", "63202900"));
        }

        // 构建只读映射缓存
        flowStationMap.clear();
        for (StationInfo station : flow.getStations()) {
            flowStationMap.put(station.getKey(), station);
        }
        waterStationMap.clear();
        for (StationInfo station : water.getStations()) {
            waterStationMap.put(station.getKey(), station);
        }
    }

    /**
     * 根据 key 快速检索流量测站
     */
    public StationInfo getFlowStation(String key) {
        return flowStationMap.get(key);
    }

    /**
     * 根据 key 快速检索水位测站
     */
    public StationInfo getWaterStation(String key) {
        return waterStationMap.get(key);
    }

    @Data
    public static class FlowConfig {
        private String baseUrl = "http://swsq.jswater.org.cn:9088";
        private String chartUrlPattern = "/jsswxxSSI/static/map/chart/0/{itemid}.png";
        private String tableUrlPattern = "/jsswxxSSI/static/map/chart/0/{itemid}_list.png";
        private List<StationInfo> stations = new ArrayList<>();
    }

    @Data
    public static class WaterConfig {
        private String url = "http://58.247.45.108:8020//RegionalWaterAnalysis/getWA_Water_Stcd";
        private List<StationInfo> stations = new ArrayList<>();
    }

    @Data
    public static class StationInfo {
        private String key;
        private String name;
        private String code;

        public StationInfo() {}

        public StationInfo(String key, String name, String code) {
            this.key = key;
            this.name = name;
            this.code = code;
        }
    }
}
