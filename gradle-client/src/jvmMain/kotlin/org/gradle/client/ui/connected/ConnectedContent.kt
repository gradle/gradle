package org.gradle.client.ui.connected

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.gradle.client.ui.composables.FailureContent
import org.gradle.client.ui.composables.Loading
import org.gradle.client.ui.composables.TitleMedium
import org.gradle.client.ui.composables.TopBar
import org.gradle.client.ui.theme.plusPaneSpacing
import org.gradle.client.ui.theme.spacing
import java.io.File

@Composable
fun ConnectedContent(component: ConnectedComponent) {
    val model by component.model.subscribeAsState()
    Scaffold(
        topBar = {
            TopBar(
                onBackClick = { component.onCloseClicked() },
                title = {
                    val rootDir = File(component.parameters.rootDir)
                    when (model) {
                        ConnectionModel.Connecting -> TitleMedium("Connecting to ${rootDir.name}")
                        is ConnectionModel.ConnectionFailure -> TitleMedium("Connection to ${rootDir.name} failed")
                        is ConnectionModel.Connected -> TitleMedium("Connected to ${rootDir.name}")
                    }
                }
            )
        }
    ) { scaffoldPadding ->
        Surface(modifier = Modifier.padding(scaffoldPadding.plusPaneSpacing())) {
            when (val current = model) {
                ConnectionModel.Connecting -> ConnectingMainContent(component)
                is ConnectionModel.ConnectionFailure -> FailureContent(current.exception)
                is ConnectionModel.Connected -> ConnectedMainContent(component, current)
            }
        }
    }
}

@Composable
private fun ConnectingMainContent(component: ConnectedComponent) {
    Column {
        Text("Connecting to ${component.parameters.rootDir}")
        Text(component.parameters.toString())
    }
}

@Composable
private fun ConnectedMainContent(component: ConnectedComponent, model: ConnectionModel.Connected) {
    EventsBottomSheetScaffold(
        events = model.events,
    ) { sheetPadding ->
        TwoPanes(
            modifier = Modifier.padding(sheetPadding),
            left = {
                // Actions
                component.modelActions.forEach { action ->
                    Row(
                        modifier = Modifier.fillMaxWidth().selectable(
                            selected = false,
                            onClick = { component.getModel(action) }
                        ).padding(MaterialTheme.spacing.level1),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.level1)
                    ) {
                        Icon(Icons.Default.PlayCircle, action.displayName)
                        Text(action.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            right = {
                // Outcome
                when (val outcome = model.outcome) {
                    Outcome.None -> Unit
                    Outcome.Building -> Loading()
                    is Outcome.Failure -> FailureContent(outcome.exception)
                    is Outcome.Result -> {
                        val action = component.actionFor(outcome.model)
                        if (action != null) {
                            action.run {
                                verticalScrollContent {
                                    ModelContent(outcome.model)
                                }
                            }
                        } else {
                            Text(
                                text = "Unknown Model Type: ${outcome.model::class.simpleName}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = outcome.model.toString(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
fun verticalScrollContent(
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val stateHorizontal = rememberScrollState(0)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 12.dp)
                .horizontalScroll(stateHorizontal)
        ) {
            Column {
                content()
            }
        }

        HorizontalScrollbar(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(),
            adapter = rememberScrollbarAdapter(stateHorizontal)
        )
    }
}
