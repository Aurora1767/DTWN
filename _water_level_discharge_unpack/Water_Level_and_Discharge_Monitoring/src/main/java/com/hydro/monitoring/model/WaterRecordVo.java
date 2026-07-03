package com.hydro.monitoring.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 水位数据传输对象 (VO)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaterRecordVo implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 监测时间
     */
    private String time;

    /**
     * 水位值 (单位: m)
     */
    private Double waterLevel;
}
