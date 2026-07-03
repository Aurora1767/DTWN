package com.hydro.monitoring.task;

import com.hydro.monitoring.service.HydroDataFetchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 水利数据自动调度与预热执行任务层
 */
@Slf4j
@Component
public class HydroDataScheduleTask implements CommandLineRunner {

    private final HydroDataFetchService fetchService;

    @Autowired
    public HydroDataScheduleTask(HydroDataFetchService fetchService) {
        this.fetchService = fetchService;
    }

    /**
     * 实现 CommandLineRunner 接口
     * 保证应用启动并初始化完毕后，立刻同步触发一次全量数据拉取，进行本地缓存预热，避免前端/网关首次调用发生空数据
     */
    @Override
    public void run(String... args) {
        log.info("【系统启动初始化】检测到 Spring Boot 容器加载完毕，开始首次同步抓取水利数据以进行缓存预热...");
        try {
            fetchService.fetchAll();
            log.info("【系统启动初始化】首次水利数据同步抓取与本地缓存预热圆满成功！");
        } catch (Exception e) {
            log.error("【系统启动初始化】首次缓存预热触发异常! 原因: {}, 系统将依靠后续每小时定时任务进行同步。", e.getMessage());
        }
    }

    /**
     * 每小时第 05 分钟，自动在后台拉取最新一期水情，更新本地持久化缓存
     * Cron 表达式: 0 5 * * * *
     */
    @Scheduled(cron = "0 5 * * * *")
    public void executeHourlyFetchTask() {
        log.info("【定时同步调度】到达每小时第 05 分钟，触发后台异步同步拉取...");
        try {
            fetchService.fetchAll();
            log.info("【定时同步调度】当前整点批次水利数据同步并缓存成功！");
        } catch (Exception e) {
            log.error("【定时同步调度】执行每小时定时同步任务时发生异常! 原因: {}", e.getMessage(), e);
        }
    }
}
