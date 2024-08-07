package org.gradle.client.ui.buildlist

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import io.github.vinceglb.filekit.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.core.FileKitPlatformSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.gradle.client.core.Constants.APPLICATION_DISPLAY_NAME
import org.gradle.client.core.database.Build
import org.gradle.client.core.util.currentDesktopOS
import org.gradle.client.ui.composables.*
import org.gradle.client.ui.theme.plusPaneSpacing
import java.io.File

@Composable
fun BuildListContent(component: BuildListComponent) {
    val snackbarState = remember { SnackbarHostState() }
    Scaffold(
        topBar = { TopBar(title = { TitleMedium(APPLICATION_DISPLAY_NAME) }) },
        floatingActionButton = { AddBuildButton(component, snackbarState) },
        snackbarHost = { SnackbarHost(snackbarState) },
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
private fun AddBuildButton(component: BuildListComponent, snackbarState: SnackbarHostState) {
    if (currentDesktopOS.isLinux) {
        LinuxAddBuildButton(component, snackbarState)
    } else {
        NonLinuxAddBuildButton(component, snackbarState)
    }
}

@Composable
private fun NonLinuxAddBuildButton(component: BuildListComponent, snackbarState: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val dirPickerLauncher = rememberDirectoryPickerLauncher(ADD_BUILD_HELP_TEXT) { rootDir ->
        onBuildDirSelected(scope, component, snackbarState, rootDir?.file)
    }
    AddBuildDirFAB { dirPickerLauncher.launch() }
}

@Composable
private fun LinuxAddBuildButton(component: BuildListComponent, snackbarState: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var isDirChooserOpen by remember { mutableStateOf(false) }
    if (isDirChooserOpen) {
        DirChooserDialog(
            helpText = ADD_BUILD_HELP_TEXT,
            onDirChosen = { rootDir ->
                isDirChooserOpen = false
                onBuildDirSelected(scope, component, snackbarState, rootDir)
            }
        )
    }
    AddBuildDirFAB { isDirChooserOpen = true }
}

private fun onBuildDirSelected(
    scope: CoroutineScope,
    component: BuildListComponent,
    snackbarState: SnackbarHostState,
    rootDir: File?
) {
    if (rootDir == null) {
        scope.launch { snackbarState.showSnackbar("No build selected") }
    } else {
        val valid = rootDir.listFiles { file ->
            file.name.startsWith("settings.gradle")
        }?.isNotEmpty() ?: false
        if (!valid) {
            scope.launch { snackbarState.showSnackbar("Directory is not a Gradle build!") }
        } else {
            component.onNewBuildRootDirChosen(rootDir)
        }
    }
}

@Composable
private fun AddBuildDirFAB(onClick: () -> Unit) {
    PlainTextTooltip(ADD_BUILD_HELP_TEXT) {
        ExtendedFloatingActionButton(
            icon = { Icon(Icons.Default.Add, "") },
            text = { Text(text = "Add build", Modifier.testTag("add_build")) },
            onClick = onClick,
        )
    }
}

private const val ADD_BUILD_HELP_TEXT = "Choose a Gradle build directory"
