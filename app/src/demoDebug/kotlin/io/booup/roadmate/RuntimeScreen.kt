package io.booup.roadmate

import androidx.compose.runtime.Composable

@Composable
fun RuntimeScreen(viewModel: RoadmateViewModel) =
    RoadmateApp(viewModel, simulationLabel = "DEMO · SIMULATED", demoControls = viewModel.developerPanel,
        parkSimulation = viewModel.parkSimulation)
