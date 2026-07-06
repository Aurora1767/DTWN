package com.project.waternet.realtime;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class RealtimeModelScheduler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeModelScheduler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String pythonCommand;
    private final Path scriptPath;
    private final Path stateFilePath;
    private final AtomicInteger stepCount = new AtomicInteger(0);

    public RealtimeModelScheduler(
            @Value("${waternet.water-quantity.python-command:python}") String pythonCommand) {
        this.pythonCommand = pythonCommand;
        Path modelDir = Path.of(System.getProperty("user.dir"))
                .getParent()
                .resolve("首页模型实时运行");
        this.scriptPath = modelDir.resolve("app.py");
        this.stateFilePath = modelDir.resolve("solver_state.pkl");
    }

    /**
     * 每60秒执行一次：调用 Python app.py --step，推进一个时步，将结果写入数据库。
     * initialDelay=5000 让后端完全启动后再开始调度。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 5_000)
    public void stepModel() {
        if (!scriptPath.toFile().exists()) {
            log.warn("[实时模型] 脚本不存在: {}", scriptPath);
            return;
        }

        int step = stepCount.incrementAndGet();
        log.info("[实时模型] 推进第 {} 步", step);

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    pythonCommand,
                    scriptPath.toString(),
                    "--step",
                    "--state-file", stateFilePath.toString()
            );
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.redirectErrorStream(false);
            Process process = pb.start();

            // 读 stdout (JSON 结果)
            String stdout;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                stdout = reader.lines()
                        .reduce("", (a, b) -> a.isBlank() ? b : a + "\n" + b);
            }

            // 读 stderr (日志输出)
            String stderr;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                stderr = reader.lines()
                        .reduce("", (a, b) -> a.isBlank() ? b : a + "\n" + b);
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("[实时模型] step={} 超时，已强制终止", step);
                return;
            }
            if (process.exitValue() != 0) {
                log.error("[实时模型] step={} 退出码={}, stderr={}", step, process.exitValue(), stderr);
                return;
            }

            // 解析 JSON 输出，记录关键节点水位
            if (!stdout.isBlank()) {
                try {
                    JsonNode root = MAPPER.readTree(stdout.trim());
                    if (root.has("nodeHeads")) {
                        JsonNode heads = root.path("nodeHeads");
                        log.info("[实时模型] step={} | 节点1={} 节点3={} 节点6={}",
                                step,
                                fmt(heads.path("1").asDouble()),
                                fmt(heads.path("3").asDouble()),
                                fmt(heads.path("6").asDouble()));
                    }
                } catch (Exception e) {
                    log.debug("[实时模型] JSON解析失败，stdout={}", stdout);
                }
            }

        } catch (Exception e) {
            log.error("[实时模型] step={} 异常: {}", step, e.getMessage(), e);
        }
    }

    private String fmt(double v) {
        return String.format("%.3f", v);
    }
}
