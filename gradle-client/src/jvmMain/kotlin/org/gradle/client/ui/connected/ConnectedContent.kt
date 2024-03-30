package org.gradle.client.ui.connected

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.gradle.client.ui.composables.BackIcon
import org.gradle.client.ui.theme.plusPaneSpacing
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.gradle.GradleBuild

@Composable
fun ConnectedContent(component: ConnectedComponent) {
    Scaffold(
        topBar = { TopBar(component) }
    ) { scaffoldPadding ->
        Surface(modifier = Modifier.padding(scaffoldPadding.plusPaneSpacing())) {
            val model by component.model.subscribeAsState()
            when (val current = model) {
                ConnectionModel.Disconnected -> DisconnectedMainContent(component)
                is ConnectionModel.Connected -> ConnectedMainContent(component, current)
            }
        }
    }
}

@Composable
private fun DisconnectedMainContent(component: ConnectedComponent) {
    Column {
        Text("Disconnected from ${component.parameters.rootDir}")
        Text(component.parameters.toString())
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ConnectedMainContent(component: ConnectedComponent, model: ConnectionModel.Connected) {
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Events") },
                icon = { Icon(Icons.Default.Checklist, contentDescription = "") },
                onClick = { showBottomSheet = true }
            )
        },
    ) { scaffoldPadding ->
        Row(Modifier.padding(scaffoldPadding)) {
            Column(
                modifier = Modifier.padding(end = 8.dp).weight(0.3f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "Get BuildEnvironment" to { component.getBuildEnvironment() },
                    "Get GradleBuild" to { component.getGradleBuild() },
                    "Get GradleProject" to { component.getGradleProject() },
                ).forEach { (name, action) ->
                    ListItem(
                        modifier = Modifier.selectable(selected = false, onClick = action),
                        headlineContent = { Text(name) },
                        trailingContent = { Icon(Icons.Default.PlayCircle, name) },
                    )
                }
            }
            Column(
                modifier = Modifier.weight(0.7f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (val result = model.result) {
                    is BuildEnvironment -> {
                        Text("Build Environment", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Build Identifier: ${result.buildIdentifier.rootDir}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text("Java Home: ${result.java.javaHome}", style = MaterialTheme.typography.labelSmall)
                        Text("JVM Arguments:  ${result.java.jvmArguments}", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "Gradle Version: ${result.gradle.gradleVersion}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            "Gradle User Home: ${result.gradle.gradleUserHome}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    is GradleBuild -> {
                        Text("Gradle Build", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Build Identifier: ${result.buildIdentifier.rootDir}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            "Root Project Identifier: ${result.rootProject.projectIdentifier}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            "Root Project Name: ${result.rootProject.name}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    is GradleProject -> {
                        Text("Gradle Project", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Root Project Identifier: ${result.projectIdentifier}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text("Root Project Name: ${result.name}", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "Root Project Description: ${result.description}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    null -> Unit
                    else -> {
                        Text(
                            "Unknown Model Type: ${result::class.simpleName}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(result.toString())
                    }
                }
            }
        }
        if (showBottomSheet) {
            ModalBottomSheet(
                modifier = Modifier.fillMaxWidth(0.85f),
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
            ) {
                if (model.events.isEmpty()) {
                    Text("No events", Modifier.padding(16.dp))
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp, bottom = 16.dp, start = 8.dp, end = 8.dp),
                    ) {
                        model.events.forEach { event ->
                            Text(event, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}


@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TopBar(component: ConnectedComponent) {
    TopAppBar(
        modifier = Modifier.padding(0.dp).height(56.dp).fillMaxWidth(),
        navigationIcon = {
            Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                BackIcon { component.onCloseClicked() }
            }
        },
        title = {
            Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                val model by component.model.subscribeAsState()
                when (model) {
                    ConnectionModel.Disconnected -> Text("Disconnected from ${component.parameters.rootDir}")
                    is ConnectionModel.Connected -> Text("Connected to ${component.parameters.rootDir}")
                }
            }
        }
    )
}