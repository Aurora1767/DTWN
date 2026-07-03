package com.hydro.monitoring.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hydro.monitoring.config.HydroProperties;
import com.hydro.monitoring.config.HydroProperties.StationInfo;
import com.hydro.monitoring.model.WaterRecordVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * 统一网关接入API层
 * 添加 @CrossOrigin 提供跨域支持
 */
@Slf4j
@CrossOrigin
@RestController
@RequestMapping("/api/hydro")
public class HydroApiController {

    private final HydroProperties properties;
    private final ObjectMapper objectMapper;

    @Autowired
    public HydroApiController(HydroProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 1. 流量控制图片反向流暴露接口
     * GET /api/hydro/flow/{stationKey}/{type}
     *
     * @param stationKey 测站Key（例如: zhihu, wangting_flow, yixing）
     * @param type       类型（chart 或 table）
     */
    @GetMapping(value = "/flow/{stationKey}/{type}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<Resource> getFlowPicture(
            @PathVariable String stationKey,
            @PathVariable String type) {

        // 验证参数
        if (!"chart".equalsIgnoreCase(type) && !"table".equalsIgnoreCase(type)) {
            log.warn("【流量图片接口】收到非法的图片格式请求类型: {}", type);
            return ResponseEntity.badRequest().build();
        }

        // 从内存 Map 中进行高性能匹配获取测站信息
        StationInfo station = properties.getFlowStation(stationKey);
        if (station == null) {
            log.warn("【流量图片接口】收到未配置的流量测站 Key 请求: {}", stationKey);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String stationCode = station.getCode();
        File imageFile = new File(properties.getCachePath(), stationCode + "_" + type.toLowerCase() + ".png");

        if (!imageFile.exists()) {
            log.warn("【流量图片接口】测站 {}({}) 的本地缓存 {} 图片暂不存在，启动内存动态占位图降级...", station.getName(), stationKey, type);
            // 本地缓存图片因为网络或初次下载暂未到达时，优雅地返回动态防裂图占位符，从而保护前端及网关稳定性
            return renderPlaceholderImage();
        }

        try {
            Resource fileResource = new FileSystemResource(imageFile);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(fileResource);
        } catch (Exception e) {
            log.error("【流量图片接口】读取本地磁盘缓存图片发生故障! 测站Key: {}, 详情: {}", stationKey, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 2. 结构化 JSON 水位序列数据获取接口
     * GET /api/hydro/water/{stationKey}
     *
     * @param stationKey 水位测站Key（例如: luoshe, wuxi, wangting_tai, wangting_lijiao, wangting_da）
     */
    @GetMapping("/water/{stationKey}")
    public ResponseEntity<List<WaterRecordVo>> getWaterRecords(@PathVariable String stationKey) {
        // 从内存 Map 中进行高性能匹配获取水位测站信息
        StationInfo station = properties.getWaterStation(stationKey);
        if (station == null) {
            log.warn("【水位数据接口】收到未配置的水位测站 Key 请求: {}", stationKey);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String stationCode = station.getCode();
        File jsonFile = new File(properties.getCachePath(), stationCode + ".json");

        if (!jsonFile.exists()) {
            log.warn("【水位数据接口】测站 {}({}) 的水位 JSON 缓存文件暂不存在，返回友好空数据集...", station.getName(), stationKey);
            // 采用空数据集，利于前端渲染而不报 500
            return ResponseEntity.ok(Collections.emptyList());
        }

        try {
            // 反序列化并返回
            List<WaterRecordVo> records = objectMapper.readValue(
                    jsonFile,
                    new TypeReference<List<WaterRecordVo>>() {}
            );
            return ResponseEntity.ok(records);
        } catch (Exception e) {
            log.error("【水位数据接口】反序列化缓存的 JSON 文件故障! 测站Key: {}, 原因: {}", stationKey, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * AWT 高端精细化虚线抗锯齿占位图无状态内存实时渲染器
     */
    private ResponseEntity<Resource> renderPlaceholderImage() {
        try {
            int width = 500;
            int height = 260;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();

            // 双抗锯齿模式，达到媲美高清晰度矢量图的设计感
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 1. 底底色填充
            g2d.setColor(new Color(244, 246, 249));
            g2d.fillRect(0, 0, width, height);

            // 2. 边框绘制
            g2d.setColor(new Color(220, 223, 230));
            Stroke stroke = new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0);
            g2d.setStroke(stroke);
            g2d.drawRect(12, 12, width - 24, height - 24);

            // 3. 绘制文字提示
            g2d.setColor(new Color(144, 147, 153));
            g2d.setFont(new Font("Microsoft YaHei", Font.BOLD, 15));
            String text1 = "外部水利系统图表同步中";
            String text2 = "Water Conservancy Graph Syncing...";

            FontMetrics fm = g2d.getFontMetrics();
            int x1 = (width - fm.stringWidth(text1)) / 2;
            g2d.drawString(text1, x1, height / 2 - 10);

            g2d.setFont(new Font("Consolas", Font.PLAIN, 12));
            fm = g2d.getFontMetrics();
            int x2 = (width - fm.stringWidth(text2)) / 2;
            g2d.drawString(text2, x2, height / 2 + 25);

            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            ByteArrayResource resource = new ByteArrayResource(baos.toByteArray());

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
