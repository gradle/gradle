package org.gradle.client.ui.build

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.launch
import org.gradle.client.core.gradle.GradleConnectionParameters
import org.gradle.client.core.gradle.GradleDistribution
import org.gradle.client.ui.composables.BackIcon
import org.gradle.client.ui.composables.DirChooserDialog
import org.gradle.client.ui.composables.Loading
import org.gradle.client.ui.composables.PlainTextTooltip
import org.gradle.client.ui.theme.plusPaneSpacing
import org.gradle.client.ui.theme.spacing
import java.io.File

@Composable
fun BuildContent(component: BuildComponent) {
    val snackbarState = remember { SnackbarHostState() }
    Scaffold(
        topBar = { TopBar(component) },
        snackbarHost = { SnackbarHost(snackbarState) },
    ) { scaffoldPadding ->
        Surface(modifier = Modifier.padding(scaffoldPadding.plusPaneSpacing())) {
            val model by component.model.subscribeAsState()
            when (val current = model) {
                BuildModel.Loading -> Loading()
                is BuildModel.Loaded -> BuildMainContent(component, current, snackbarState)
            }
        }
    }
}

enum class GradleDistSource(
    val displayName: String
) {
    DEFAULT("Default/Wrapper"),
    VERSION("Specific Version"),
    LOCAL("Local Installation")
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun BuildMainContent(
    component: BuildComponent,
    model: BuildModel.Loaded,
    snackbarState: SnackbarHostState,
) {

    val scope = rememberCoroutineScope()

    var javaHome by rememberSaveable { mutableStateOf(System.getenv("JAVA_HOME") ?: "") }
    var gradleUserHome by rememberSaveable { mutableStateOf("") }
    var gradleDistSource by rememberSaveable { mutableStateOf(GradleDistSource.DEFAULT) }
    var gradleDistVersion by rememberSaveable { mutableStateOf("") }
    var gradleDistLocalDir by rememberSaveable { mutableStateOf("") }

    val isJavaHomeValid by derivedStateOf {
        javaHome.isNotBlank() && File(javaHome).let {
            it.isDirectory && it.resolve("bin").listFiles { file ->
                file.nameWithoutExtension == "java"
            }?.isNotEmpty() ?: false
        }
    }
    val isGradleUserHomeValid by derivedStateOf {
        gradleUserHome.isBlank() || File(gradleUserHome).let { !it.exists() || it.isDirectory }
    }
    val isGradleDistVersionValid by derivedStateOf {
        gradleDistVersion.isNotBlank()
    }
    val isGradleDistLocalDirValid by derivedStateOf {
        gradleDistLocalDir.isNotBlank() && File(gradleDistLocalDir).resolve("bin").listFiles { file ->
            file.nameWithoutExtension == "gradle"
        }?.isNotEmpty() ?: false
    }
    val isCanConnect by derivedStateOf {
        isJavaHomeValid && isGradleUserHomeValid &&
                when (gradleDistSource) {
                    GradleDistSource.DEFAULT -> true
                    GradleDistSource.VERSION -> isGradleDistVersionValid
                    GradleDistSource.LOCAL -> isGradleDistLocalDirValid
                }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.level4)
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = model.build.rootDir.absolutePath,
            readOnly = true,
            onValueChange = {},
            label = { Text("Root directory") }
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = javaHome,
            readOnly = false,
            onValueChange = { javaHome = it },
            label = { Text("Java Home") },
            isError = !isJavaHomeValid,
            trailingIcon = {
                val helpText = "Select a Java home"
                var isDirChooserOpen by remember { mutableStateOf(false) }
                if (isDirChooserOpen) {
                    DirChooserDialog(
                        helpText = helpText,
                        showHiddenFiles = true,
                        onDirChosen = { dir ->
                            isDirChooserOpen = false
                            if (dir == null) {
                                scope.launch { snackbarState.showSnackbar("No Java home selected") }
                            } else {
                                javaHome = dir.absolutePath
                            }
                        }
                    )
                }
                Row {
                    IconButton(
                        enabled = javaHome.isNotBlank(),
                        onClick = { javaHome = "" },
                        content = { Icon(Icons.Default.Clear, "Clear") }
                    )
                    PlainTextTooltip(helpText) {
                        IconButton(onClick = { isDirChooserOpen = true }) {
                            Icon(Icons.Default.Folder, helpText)
                        }
                    }
                }
            }
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = gradleUserHome,
            readOnly = false,
            onValueChange = { gradleUserHome = it },
            label = { Text("Gradle User Home") },
            placeholder = { Text(System.getProperty("user.home") + "/.gradle", color = Color.Gray) },
            isError = !isGradleUserHomeValid,
            trailingIcon = {
                val helpText = "Select a Gradle user home"
                var isDirChooserOpen by remember { mutableStateOf(false) }
                if (isDirChooserOpen) {
                    DirChooserDialog(
                        helpText = helpText,
                        showHiddenFiles = true,
                        onDirChosen = { dir ->
                            isDirChooserOpen = false
                            if (dir == null) {
                                scope.launch { snackbarState.showSnackbar("No Gradle user home selected") }
                            } else {
                                gradleUserHome = dir.absolutePath
                            }
                        }
                    )
                }
                Row {
                    IconButton(
                        enabled = gradleUserHome.isNotBlank(),
                        onClick = { gradleUserHome = "" },
                        content = { Icon(Icons.Default.Clear, "Clear") }
                    )
                    PlainTextTooltip(helpText) {
                        IconButton(onClick = { isDirChooserOpen = true }) {
                            Icon(Icons.Default.Folder, helpText)
                        }
                    }
                }
            }
        )
        var sourceMenuExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = sourceMenuExpanded,
            onExpandedChange = { sourceMenuExpanded = !sourceMenuExpanded }
        ) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                readOnly = true,
                value = gradleDistSource.displayName,
                onValueChange = {},
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceMenuExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                label = { Text("Gradle distribution") }
            )
            ExposedDropdownMenu(
                expanded = sourceMenuExpanded,
                onDismissRequest = { sourceMenuExpanded = false },
            ) {
                GradleDistSource.entries.forEach { source ->
                    DropdownMenuItem(
                        text = { Text(source.displayName) },
                        onClick = {
                            gradleDistSource = source
                            sourceMenuExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
        when (gradleDistSource) {
            GradleDistSource.DEFAULT -> Unit
            GradleDistSource.VERSION -> {
                val versions by component.gradleVersions.subscribeAsState()
                var versionMenuExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = versionMenuExpanded,
                    onExpandedChange = { versionMenuExpanded = !versionMenuExpanded }
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        readOnly = false,
                        value = gradleDistVersion,
                        onValueChange = { gradleDistVersion = it },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceMenuExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        label = { Text("Gradle Version") },
                        isError = !isGradleDistVersionValid
                    )
                    ExposedDropdownMenu(
                        expanded = versionMenuExpanded,
                        onDismissRequest = { versionMenuExpanded = false }
                    ) {
                        versions.forEach { version ->
                            DropdownMenuItem(
                                text = { Text(version) },
                                onClick = {
                                    gradleDistVersion = version
                                    versionMenuExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }
            }

            GradleDistSource.LOCAL -> {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = gradleDistLocalDir,
                    onValueChange = {
                        gradleDistLocalDir = it
                    },
                    label = { Text("Local installation path") },
                    isError = !isGradleDistLocalDirValid,
                    trailingIcon = {
                        val helpText = "Select a Gradle installation"
                        var isDirChooserOpen by remember { mutableStateOf(false) }
                        if (isDirChooserOpen) {
                            DirChooserDialog(
                                helpText = helpText,
                                onDirChosen = { dir ->
                                    isDirChooserOpen = false
                                    if (dir == null) {
                                        scope.launch {
                                            snackbarState.showSnackbar("No Gradle installation selected")
                                        }
                                    } else {
                                        gradleDistLocalDir = dir.absolutePath
                                    }
                                }
                            )
                        }
                        Row {
                            IconButton(
                                enabled = gradleDistLocalDir.isNotBlank(),
                                onClick = { gradleDistLocalDir = "" },
                                content = { Icon(Icons.Default.Clear, "Clear") }
                            )
                            PlainTextTooltip(helpText) {
                                IconButton(onClick = { isDirChooserOpen = true }) {
                                    Icon(Icons.Default.Folder, helpText)
                                }
                            }
                        }
                    }
                )
            }
        }

        Button(
            enabled = isCanConnect,
            content = { Text("Connect") },
            onClick = {
                component.onConnectClicked(
                    GradleConnectionParameters(
                        rootDir = model.build.rootDir.absolutePath,
                        javaHomeDir = javaHome.takeIf { it.isNotBlank() },
                        gradleUserHomeDir = gradleUserHome.takeIf { it.isNotBlank() },
                        distribution = when (gradleDistSource) {
                            GradleDistSource.DEFAULT -> GradleDistribution.Default
                            GradleDistSource.VERSION -> GradleDistribution.Version(gradleDistVersion)
                            GradleDistSource.LOCAL -> GradleDistribution.Local(gradleDistLocalDir)
                        }
                    )
                )
            },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TopBar(component: BuildComponent) {
    val model by component.model.subscribeAsState()
    TopAppBar(
        modifier = Modifier.padding(MaterialTheme.spacing.level0)
            .height(MaterialTheme.spacing.topBarHeight)
            .fillMaxWidth(),
        navigationIcon = {
            Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                BackIcon { component.onCloseClicked() }
            }
        },
        title = {
            Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                when (val current = model) {
                    BuildModel.Loading -> Text("Build")
                    is BuildModel.Loaded -> Text(current.build.rootDir.name)
                }
            }
        }
    )
}
