package com.project.waternet.network;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.waternet.common.ApiResponse;

@RestController
@RequestMapping("/api/network")
public class NetworkController {

	private final NetworkMockService networkMockService;
	private final NetworkDatabaseService networkDatabaseService;

	public NetworkController(NetworkMockService networkMockService, NetworkDatabaseService networkDatabaseService) {
		this.networkMockService = networkMockService;
		this.networkDatabaseService = networkDatabaseService;
	}

	@GetMapping("/overview")
	public ApiResponse<Map<String, Object>> overview() {
		return networkDatabaseService.overview()
				.map(ApiResponse::ok)
				.orElseGet(() -> ApiResponse.ok(Map.of(
						"segments", networkMockService.segments(),
						"nodes", networkMockService.nodes(),
						"structures", networkMockService.structures())));
	}

	@GetMapping("/overview/mock")
	public ApiResponse<Map<String, Object>> mockOverview() {
		return ApiResponse.ok(Map.of(
				"segments", networkMockService.segments(),
				"nodes", networkMockService.nodes(),
				"structures", networkMockService.structures()));
	}

	@GetMapping("/segments")
	public ApiResponse<Object> segments() {
		return ApiResponse.ok(networkDatabaseService.segments().orElseGet(networkMockService::segments));
	}

	@GetMapping("/nodes")
	public ApiResponse<Object> nodes() {
		return ApiResponse.ok(networkDatabaseService.nodes().orElseGet(networkMockService::nodes));
	}

	@GetMapping("/structures")
	public ApiResponse<Object> structures() {
		return ApiResponse.ok(networkMockService.structures());
	}

	@GetMapping("/segments/{segmentCode}/profile")
	public ApiResponse<Object> segmentProfile(@PathVariable String segmentCode) {
		return networkDatabaseService.segmentProfile(segmentCode)
				.<ApiResponse<Object>>map(ApiResponse::ok)
				.orElseGet(() -> ApiResponse.error(404, "河段沿程数据不存在: " + segmentCode));
	}

	@GetMapping("/nodes/{nodeCode}/series")
	public ApiResponse<Object> nodeSeries(
			@PathVariable String nodeCode,
			@RequestParam(name = "recentHours", defaultValue = "72") int recentHours) {
		return networkDatabaseService.nodeSeries(nodeCode, recentHours)
				.<ApiResponse<Object>>map(ApiResponse::ok)
				.orElseGet(() -> ApiResponse.error(404, "节点过程线数据不存在: " + nodeCode));
	}
}
