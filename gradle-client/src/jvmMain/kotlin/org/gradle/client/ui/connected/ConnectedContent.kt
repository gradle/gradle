package org.gradle.client.ui.connected

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.gradle.client.ui.composables.Loading
import org.gradle.client.ui.composables.TopBar
import org.gradle.client.ui.theme.plusPaneSpacing

@Composable
fun ConnectedContent(component: ConnectedComponent) {
    val model by component.model.subscribeAsState()
    Scaffold(
        topBar = {
            TopBar(
                onBackClick = { component.onCloseClicked() },
                title = {
                    val rootDir = component.parameters.rootDir
                    when (model) {
                        ConnectionModel.Connecting -> Text("Connecting to $rootDir")
                        is ConnectionModel.ConnectionFailure -> Text("Connection to $rootDir failed")
                        is ConnectionModel.Connected -> Text("Connected to $rootDir")
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
private fun FailureContent(exception: Exception) {
    Column(
        modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())
    ) {
        Text(
            text = exception.stackTraceToString(),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.error,
        )
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
                    ListItem(
                        modifier = Modifier.selectable(
                            selected = false,
                            onClick = { component.getModel(action.modelType) }
                        ),
                        leadingContent = { Icon(Icons.Default.PlayCircle, action.displayName) },
                        headlineContent = { Text(action.displayName, style = MaterialTheme.typography.titleSmall) },
                    )
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
                                ModelContent(outcome.model)
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
