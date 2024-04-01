package org.gradle.client.ui.buildlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.gradle.client.logic.Constants.APPLICATION_DISPLAY_NAME
import org.gradle.client.logic.build.Build
import org.gradle.client.ui.composables.Loading
import org.gradle.client.ui.composables.PathChooserDialog
import org.gradle.client.ui.composables.PlainTextTooltip
import org.gradle.client.ui.theme.plusPaneSpacing

@Composable
fun BuildListContent(component: BuildListComponent) {
    Scaffold(
        topBar = { TopBar() },
        floatingActionButton = { AddBuildButton(component) },
    ) { scaffoldPadding ->
        Surface(Modifier.padding(scaffoldPadding.plusPaneSpacing())) {
            val model by component.model.subscribeAsState()
            when (val current = model) {
                BuildListModel.Loading -> Loading()
                is BuildListModel.Loaded -> BuildsList(component, current)
            }
        }
    }
}

@Composable
private fun BuildsList(component: BuildListComponent, model: BuildListModel.Loaded) {
    LazyColumn {
        items(items = model.builds, key = { it.id }) { build ->
            ListItem(
                modifier = Modifier.selectable(
                    selected = false,
                    onClick = { component.onBuildClicked(build) }
                ),
                leadingContent = { BuildListIcon() },
                headlineContent = { Text(build.rootDir.name) },
                supportingContent = { Text(build.rootDir.absolutePath) },
                trailingContent = { BuildListDeleteButon(component, build) }
            )
        }
    }
}

@Composable
private fun BuildListIcon() {
    Icon(
        modifier = Modifier.size(36.dp),
        painter = painterResource(resourcePath = "/icons/icon_gradle_rgb.png"),
        contentDescription = "Gradle Build"
    )
}

@Composable
private fun BuildListDeleteButon(component: BuildListComponent, build: Build) {
    PlainTextTooltip("Delete") {
        IconButton(
            onClick = { component.onDeleteBuildClicked(build) },
            content = { Icon(Icons.Default.Close, "Close") }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TopBar() {
    TopAppBar(
        modifier = Modifier.padding(0.dp).height(56.dp).fillMaxWidth(),
        title = {
            Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Text(APPLICATION_DISPLAY_NAME)
            }
        }
    )
}

@Composable
private fun AddBuildButton(component: BuildListComponent) {
    var isPathChooserOpen by remember { mutableStateOf(false) }
    if (isPathChooserOpen) {
        PathChooserDialog(
            helpText = addBuildHelpText,
            selectableFilter = { path ->
                path.isFile && path.name.startsWith("settings.gradle")
            },
            choiceMapper = { path ->
                path.parentFile
            },
            onPathChosen = { rootDir ->
                isPathChooserOpen = false
                if (rootDir != null) {
                    component.onNewBuildRootDirChosen(rootDir)
                }
            }
        )
    }
    PlainTextTooltip(addBuildHelpText) {
        ExtendedFloatingActionButton(
            icon = { Icon(Icons.Default.Add, "") },
            text = { Text(text = "Add build", Modifier.testTag("add_build")) },
            onClick = { isPathChooserOpen = true },
        )
    }
}

private const val addBuildHelpText = "Choose a Gradle settings script"
