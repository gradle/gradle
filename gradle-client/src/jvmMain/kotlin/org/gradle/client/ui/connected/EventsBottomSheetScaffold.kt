package org.gradle.client.ui.connected

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.gradle.client.ui.theme.spacing

private val SHEET_PEEK_HEIGHT = 48.dp
private val SHEET_MAX_WIDTH = 4000.dp
private const val SHEET_MAX_HEIGHT = 0.75f

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun EventsBottomSheetScaffold(
    events: List<Event>,
    content: @Composable (sheetPadding: PaddingValues) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetScaffoldState = rememberBottomSheetScaffoldState()
    val eventsListState = rememberLazyListState()
    LaunchedEffect(events) {
        if (events.isEmpty()) {
            sheetScaffoldState.bottomSheetState.partialExpand()
        }
        eventsListState.animateScrollToItem(eventsListState.layoutInfo.totalItemsCount)
    }
    BottomSheetScaffold(
        scaffoldState = sheetScaffoldState,
        sheetPeekHeight = SHEET_PEEK_HEIGHT,
        sheetMaxWidth = SHEET_MAX_WIDTH,
        sheetDragHandle = {
            Row(
                modifier = Modifier.height(SHEET_PEEK_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.level2),
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
                    enabled = events.isNotEmpty(),
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
                            scope.launch {
                                eventsListState.animateScrollToItem(eventsListState.layoutInfo.totalItemsCount)
                            }
                        }
                    ) {
                        Icon(Icons.Default.ArrowDownward, "Bottom")
                    }
                }
            }
        },
        sheetContent = {
            if (events.isEmpty()) {
                Text("No events", Modifier.padding(MaterialTheme.spacing.level4))
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .fillMaxHeight(SHEET_MAX_HEIGHT)
                        .padding(
                            top = MaterialTheme.spacing.level2,
                            bottom = MaterialTheme.spacing.level4,
                            start = MaterialTheme.spacing.level2,
                            end = MaterialTheme.spacing.level2,
                        )
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        state = eventsListState,
                    ) {
                        items(events, { it }) { event ->
                            Text(
                                event.text,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                overflow = TextOverflow.Visible
                            )
                        }
                        item {
                            Spacer(Modifier.size(MaterialTheme.spacing.level2))
                        }
                    }
                }
            }
        },
        content = content
    )
}
