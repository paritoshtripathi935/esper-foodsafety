package com.esper.probesimulator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.esper.probesimulator.scenarios.ALL_SCENARIOS
import com.esper.probesimulator.scenarios.Scenario

@Composable
fun ScenarioSelector(
    activeScenario: Scenario?,
    onStart: (Scenario) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text("Scenario", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        ALL_SCENARIOS.forEach { scenario ->
            val isActive = scenario == activeScenario
            OutlinedButton(
                onClick = { if (isActive) onStop() else onStart(scenario) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                colors = if (isActive)
                    ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                else
                    ButtonDefaults.outlinedButtonColors(),
            ) {
                Text(
                    text = if (isActive) "⏹ ${scenario.name}" else "▶ ${scenario.name}",
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
