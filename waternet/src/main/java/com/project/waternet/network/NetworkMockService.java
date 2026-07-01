package com.project.waternet.network;

import java.util.List;

import org.springframework.stereotype.Service;

import com.project.waternet.network.dto.HydraulicStructure;
import com.project.waternet.network.dto.RiverSegment;
import com.project.waternet.network.dto.RiverSegment.Coordinate;
import com.project.waternet.network.dto.WaterNode;

@Service
public class NetworkMockService {

	public List<RiverSegment> segments() {
		return List.of(
				segment("Lihe_S1", "蠡河 S1", 1400, 20, 0.03, "N_LH_TH", "N_LH_MID",
						coords(120.229, 31.503, 120.238, 31.493, 120.247, 31.482)),
				segment("Lihe_S2", "蠡河 S2", 6400, 20, 0.03, "N_LH_MID", "N_LH_JH",
						coords(120.247, 31.482, 120.265, 31.490, 120.286, 31.501)),
				segment("Jinghang_S1", "京杭运河 S1", 1500, 105, 0.03, "N_JH_N", "N_JH_C",
						coords(120.301, 31.525, 120.305, 31.512, 120.309, 31.499)),
				segment("Jinghang_S2", "京杭运河 S2", 6100, 105, 0.02, "N_JH_C", "N_JH_S",
						coords(120.309, 31.499, 120.312, 31.482, 120.317, 31.466)),
				segment("Jinghang_S3", "京杭运河 S3", 1500, 105, 0.02, "N_JH_S", "N_DX_E",
						coords(120.317, 31.466, 120.310, 31.455, 120.303, 31.447)),
				segment("Daxigang", "大溪港", 5200, 30, 0.03, "N_DX_W", "N_DX_E",
						coords(120.241, 31.445, 120.273, 31.445, 120.303, 31.447)));
	}

	public List<WaterNode> nodes() {
		return List.of(
				new WaterNode("N_LH_TH", "蠡河太湖边界", "BOUNDARY", 120.229, 31.503, 2.5, "WATER_LEVEL"),
				new WaterNode("N_LH_MID", "蠡河中段", "JUNCTION", 120.247, 31.482, 2.48, "NONE"),
				new WaterNode("N_LH_JH", "蠡河-京杭运河交汇", "JUNCTION", 120.286, 31.501, 2.47, "NONE"),
				new WaterNode("N_JH_N", "京杭运河北边界", "BOUNDARY", 120.301, 31.525, 2.5, "WATER_LEVEL"),
				new WaterNode("N_JH_C", "京杭运河中心节点", "JUNCTION", 120.309, 31.499, 2.46, "NONE"),
				new WaterNode("N_JH_S", "京杭运河南段节点", "JUNCTION", 120.317, 31.466, 2.44, "NONE"),
				new WaterNode("N_DX_W", "大溪港西边界", "BOUNDARY", 120.241, 31.445, 2.43, "FLOW"),
				new WaterNode("N_DX_E", "大溪港东汇口", "JUNCTION", 120.303, 31.447, 2.42, "NONE"));
	}

	public List<HydraulicStructure> structures() {
		return List.of(
				new HydraulicStructure("GATE_LH_01", "蠡河节制闸", "GATE", "N_LH_MID", 80, "OPEN", 120.247, 31.482),
				new HydraulicStructure("PUMP_DX_01", "大溪港泵站", "PUMP", "N_DX_W", 45, "STANDBY", 120.241, 31.445),
				new HydraulicStructure("GATE_JH_01", "京杭运河调节闸", "GATE", "N_JH_C", 120, "OPEN", 120.309, 31.499));
	}

	private RiverSegment segment(String code, String name, double length, double width, double manningN,
			String startNode, String endNode, List<Coordinate> coordinates) {
		return new RiverSegment(code, name, length, width, manningN, startNode, endNode, coordinates);
	}

	private List<Coordinate> coords(double... values) {
		return java.util.stream.IntStream.range(0, values.length / 2)
				.mapToObj(index -> new Coordinate(values[index * 2], values[index * 2 + 1]))
				.toList();
	}
}
