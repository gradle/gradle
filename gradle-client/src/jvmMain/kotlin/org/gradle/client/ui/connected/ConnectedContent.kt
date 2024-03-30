package org.gradle.client.ui.connected

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    val sheetScaffoldState = rememberBottomSheetScaffoldState()
    val eventsListState = rememberLazyListState()
    val hasEvents by derivedStateOf { model.events.isNotEmpty() }
    LaunchedEffect(hasEvents) {
        if (!hasEvents) {
            sheetScaffoldState.bottomSheetState.partialExpand()
        }
    }
    BottomSheetScaffold(
        scaffoldState = sheetScaffoldState,
        sheetPeekHeight = 48.dp,
        sheetMaxWidth = 4000.dp,
        sheetDragHandle = {
            Row(
                modifier = Modifier.height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (sheetScaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
                    IconButton(
                        onClick = {
                            scope.launch { eventsListState.animateScrollToItem(0) }
                        }
                    ) {
                        Icon(Icons.Default.ArrowUpward, "Top")
                    }
                }
                IconButton(
                    enabled = hasEvents,
                    onClick = {
                        scope.launch {
                            when (sheetScaffoldState.bottomSheetState.currentValue) {
                                SheetValue.Hidden -> sheetScaffoldState.bottomSheetState.expand()
                                SheetValue.PartiallyExpanded -> sheetScaffoldState.bottomSheetState.expand()
                                SheetValue.Expanded -> sheetScaffoldState.bottomSheetState.partialExpand()
                            }
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, "Events")
                }
                if (sheetScaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
                    IconButton(
                        onClick = {
                            scope.launch { eventsListState.animateScrollToItem(eventsListState.layoutInfo.totalItemsCount) }
                        }
                    ) {
                        Icon(Icons.Default.ArrowDownward, "Bottom")
                    }
                }

            }
        },
        sheetContent = {
            if (model.events.isEmpty()) {
                Text("No events", Modifier.padding(16.dp))
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f)
                        .padding(top = 8.dp, bottom = 16.dp, start = 8.dp, end = 8.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        state = eventsListState,
                    ) {
                        items(model.events, { it }) { event ->
                            Text(
                                event.text,
                                style = MaterialTheme.typography.labelSmall,
                                overflow = TextOverflow.Visible
                            )
                        }
                    }
                }
            }
        }
    ) { sheetPadding ->
        Row(Modifier.padding(sheetPadding)) {

            // Actions
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
                        headlineContent = { Text(name, style = MaterialTheme.typography.titleSmall) },
                        trailingContent = { Icon(Icons.Default.PlayCircle, name) },
                    )
                }
            }

            // Result
            Column(
                modifier = Modifier.weight(0.7f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (val result = model.result) {

                    null -> Unit

                    is BuildEnvironment -> {
                        Text(
                            text = "Build Environment",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Build Identifier: ${result.buildIdentifier.rootDir}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "Java Home: ${result.java.javaHome}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "JVM Arguments:  ${result.java.jvmArguments}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "Gradle Version: ${result.gradle.gradleVersion}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "Gradle User Home: ${result.gradle.gradleUserHome}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    is GradleBuild -> {
                        Text(
                            text = "Gradle Build",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Build Identifier: ${result.buildIdentifier.rootDir}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "Root Project Identifier: ${result.rootProject.projectIdentifier}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "Root Project Name: ${result.rootProject.name}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    is GradleProject -> {
                        Text(
                            text = "Gradle Project",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Root Project Identifier: ${result.projectIdentifier}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "Root Project Name: ${result.name}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "Root Project Description: ${result.description}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    else -> {
                        Text(
                            text = "Unknown Model Type: ${result::class.simpleName}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = result.toString(),
                            style = MaterialTheme.typography.labelSmall
                        )
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