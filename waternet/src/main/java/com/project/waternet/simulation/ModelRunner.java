package com.project.waternet.simulation;

import com.project.waternet.simulation.dto.SimulationRequest;
import com.project.waternet.simulation.dto.SimulationResult;

public interface ModelRunner {

	SimulationResult run(SimulationRequest request);
}
