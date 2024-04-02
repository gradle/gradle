package org.gradle.client.ui.connected

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.launch
import org.gradle.client.ui.composables.BackIcon
import org.gradle.client.ui.composables.Loading
import org.gradle.client.ui.theme.plusPaneSpacing

@Composable
fun ConnectedContent(component: ConnectedComponent) {
    Scaffold(
        topBar = { TopBar(component) }
    ) { scaffoldPadding ->
        Surface(modifier = Modifier.padding(scaffoldPadding.plusPaneSpacing())) {
            val model by component.model.subscribeAsState()
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
@OptIn(ExperimentalMaterial3Api::class)
private fun ConnectedMainContent(component: ConnectedComponent, model: ConnectionModel.Connected) {
    val scope = rememberCoroutineScope()
    val sheetScaffoldState = rememberBottomSheetScaffoldState()
    val eventsListState = rememberLazyListState()
    LaunchedEffect(model.events) {
        if (model.events.isEmpty()) {
            sheetScaffoldState.bottomSheetState.partialExpand()
        }
        eventsListState.animateScrollToItem(eventsListState.layoutInfo.totalItemsCount)
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
                        enabled = eventsListState.canScrollBackward,
                        onClick = {
                            scope.launch { eventsListState.animateScrollToItem(0) }
                        }
                    ) {
                        Icon(Icons.Default.ArrowUpward, "Top")
                    }
                }
                IconButton(
                    enabled = model.events.isNotEmpty(),
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
                        enabled = eventsListState.canScrollForward,
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
                                fontFamily = FontFamily.Monospace,
                                overflow = TextOverflow.Visible
                            )
                        }
                        item {
                            Spacer(Modifier.size(8.dp))
                        }
                    }
                }
            }
        }
    ) { sheetPadding ->
        Row(Modifier.padding(sheetPadding)) {

            // Actions
            Column(
                modifier = Modifier.padding(end = 8.dp)
                    .weight(0.3f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
            }

            // Outcome
            Column(
                modifier = Modifier.weight(0.7f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                    ConnectionModel.Connecting -> Text("Connecting to ${component.parameters.rootDir}")
                    is ConnectionModel.ConnectionFailure -> Text("Connection to ${component.parameters.rootDir} failed")
                    is ConnectionModel.Connected -> Text("Connected to ${component.parameters.rootDir}")
                }
            }
        }
    )
}