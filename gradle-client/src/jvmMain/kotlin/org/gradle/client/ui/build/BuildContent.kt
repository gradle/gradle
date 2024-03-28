package org.gradle.client.ui.build

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.gradle.client.logic.gradle.GradleConnectionParameters
import org.gradle.client.ui.composables.BackIcon
import org.gradle.client.ui.composables.Loading
import org.gradle.client.ui.composables.PathChooserDialog
import org.gradle.client.ui.composables.PlainTextTooltip
import org.gradle.client.logic.gradle.GradleDistribution
import org.gradle.client.ui.theme.plusPaneSpacing

@Composable
fun BuildContent(component: BuildComponent) {
    Scaffold(
        topBar = { TopBar(component) }
    ) { scaffoldPadding ->
        Surface(modifier = Modifier.padding(scaffoldPadding.plusPaneSpacing())) {
            val model by component.model.subscribeAsState()
            when (val current = model) {
                BuildModel.Loading -> Loading()
                is BuildModel.Loaded -> BuildMainContent(component, current)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuildMainContent(component: BuildComponent, model: BuildModel.Loaded) {

    var javaHome by remember { mutableStateOf(System.getenv("JAVA_HOME") ?: "") }
    var gradleDistSource by remember { mutableStateOf(GradleDistSource.DEFAULT) }
    var gradleDistVersion by remember { mutableStateOf("") }
    var gradleDistLocalDir by remember { mutableStateOf("") }

    val isJavaHomeValid by derivedStateOf { javaHome.isNotBlank() }
    val isGradleDistVersionValid by derivedStateOf { gradleDistVersion.isNotBlank() }
    val isGradleDistLocalDirValid by derivedStateOf { gradleDistLocalDir.isNotBlank() }
    val isCanConnect by derivedStateOf {
        isJavaHomeValid && when (gradleDistSource) {
            GradleDistSource.DEFAULT -> true
            GradleDistSource.VERSION -> isGradleDistVersionValid
            GradleDistSource.LOCAL -> isGradleDistLocalDirValid
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
            onValueChange = { javaHome = it },
            label = { Text("Java Home") },
            isError = !isJavaHomeValid,
            trailingIcon = {
                val helpText = "Select a java executable"
                var isPathChooserOpen by remember { mutableStateOf(false) }
                if (isPathChooserOpen) {
                    PathChooserDialog(
                        helpText = helpText,
                        selectableFilter = { path -> path.isFile && path.nameWithoutExtension == "java" },
                        choiceMapper = { path -> path.parentFile.parentFile },
                        onPathChosen = { path ->
                            isPathChooserOpen = false
                            if (path != null) {
                                javaHome = path.absolutePath
                            }
                        }
                    )
                }
                PlainTextTooltip(helpText) {
                    IconButton(onClick = { isPathChooserOpen = true }) {
                        Icon(Icons.Default.Folder, helpText)
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
                        val helpText = "Select a gradle executable"
                        var isPathChooserOpen by remember { mutableStateOf(false) }
                        if (isPathChooserOpen) {
                            PathChooserDialog(
                                helpText = helpText,
                                selectableFilter = { path -> path.isFile && path.nameWithoutExtension == "gradle" },
                                choiceMapper = { path -> path.parentFile.parentFile },
                                onPathChosen = { path ->
                                    isPathChooserOpen = false
                                    if (path != null) {
                                        gradleDistLocalDir = path.absolutePath
                                    }
                                }
                            )
                        }
                        PlainTextTooltip(helpText) {
                            IconButton(onClick = { isPathChooserOpen = true }) {
                                Icon(Icons.Default.Folder, helpText)
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
                        model.build.rootDir.absolutePath,
                        javaHome,
                        when (gradleDistSource) {
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
        modifier = Modifier.padding(0.dp).height(56.dp).fillMaxWidth(),
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