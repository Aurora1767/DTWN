package com.project.waternet.network;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.waternet.common.ApiResponse;

@RestController
@RequestMapping("/api/network")
public class NetworkController {

	private final NetworkMockService networkMockService;

	public NetworkController(NetworkMockService networkMockService) {
		this.networkMockService = networkMockService;
	}

	@GetMapping("/overview")
	public ApiResponse<Map<String, Object>> overview() {
		return ApiResponse.ok(Map.of(
				"segments", networkMockService.segments(),
				"nodes", networkMockService.nodes(),
				"structures", networkMockService.structures()));
	}

	@GetMapping("/segments")
	public ApiResponse<Object> segments() {
		return ApiResponse.ok(networkMockService.segments());
	}

	@GetMapping("/nodes")
	public ApiResponse<Object> nodes() {
		return ApiResponse.ok(networkMockService.nodes());
	}

	@GetMapping("/structures")
	public ApiResponse<Object> structures() {
		return ApiResponse.ok(networkMockService.structures());
	}
}
