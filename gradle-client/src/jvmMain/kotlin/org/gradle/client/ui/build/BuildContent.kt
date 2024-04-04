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
import org.gradle.client.core.gradle.GradleConnectionParameters.Companion.isValidGradleInstallation
import org.gradle.client.core.gradle.GradleConnectionParameters.Companion.isValidGradleUserHome
import org.gradle.client.core.gradle.GradleConnectionParameters.Companion.isValidGradleVersion
import org.gradle.client.core.gradle.GradleConnectionParameters.Companion.isValidJavaHome
import org.gradle.client.core.gradle.GradleDistribution
import org.gradle.client.ui.composables.DirChooserDialog
import org.gradle.client.ui.composables.Loading
import org.gradle.client.ui.composables.PlainTextTooltip
import org.gradle.client.ui.composables.TopBar
import org.gradle.client.ui.theme.plusPaneSpacing
import org.gradle.client.ui.theme.spacing

@Composable
fun BuildContent(component: BuildComponent) {
    val model by component.model.subscribeAsState()
    val snackbarState = remember { SnackbarHostState() }
    Scaffold(
        topBar = {
            TopBar(
                onBackClick = { component.onCloseClicked() },
                title = {
                    when (val current = model) {
                        BuildModel.Loading -> Text("Build")
                        is BuildModel.Loaded -> Text(current.build.rootDir.name)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) },
    ) { scaffoldPadding ->
        Surface(modifier = Modifier.padding(scaffoldPadding.plusPaneSpacing())) {
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
private fun BuildMainContent(
    component: BuildComponent,
    model: BuildModel.Loaded,
    snackbarState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()

    val javaHome = rememberSaveable { mutableStateOf(System.getenv("JAVA_HOME") ?: "") }
    val gradleUserHome = rememberSaveable { mutableStateOf("") }
    val gradleDistSource = rememberSaveable { mutableStateOf(GradleDistSource.DEFAULT) }
    val gradleDistVersion = rememberSaveable { mutableStateOf("") }
    val gradleDistLocalDir = rememberSaveable { mutableStateOf("") }

    val isJavaHomeValid by derivedStateOf { isValidJavaHome(javaHome.value) }
    val isGradleUserHomeValid by derivedStateOf { isValidGradleUserHome(gradleUserHome.value) }
    val isGradleDistVersionValid by derivedStateOf { isValidGradleVersion(gradleDistVersion.value) }
    val isGradleDistLocalDirValid by derivedStateOf { isValidGradleInstallation(gradleDistLocalDir.value) }
    val isCanConnect by derivedStateOf {
        isJavaHomeValid && isGradleUserHomeValid &&
                when (gradleDistSource.value) {
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
        BuildTextField(
            value = model.build.rootDir.absolutePath,
            readOnly = true,
            onValueChange = {},
            label = { Text("Root directory") }
        )
        DirectoryField(
            description = "Java Home",
            state = javaHome,
            isError = !isJavaHomeValid,
            showHiddenFiles = true,
            showSnackbar = { message -> scope.launch { snackbarState.showSnackbar(message) } }
        )
        DirectoryField(
            description = "Gradle User Home",
            state = gradleUserHome,
            defaultState = System.getProperty("user.home") + "/.gradle",
            isError = !isGradleUserHomeValid,
            showHiddenFiles = true,
            showSnackbar = { message -> scope.launch { snackbarState.showSnackbar(message) } },
        )
        GradleDistributionField(
            state = gradleDistSource,
        )
        when (gradleDistSource.value) {
            GradleDistSource.DEFAULT -> Unit
            GradleDistSource.VERSION -> {
                GradleVersionField(
                    component = component,
                    state = gradleDistVersion,
                    isError = !isGradleDistVersionValid,
                )
            }

            GradleDistSource.LOCAL -> {
                DirectoryField(
                    "Local Gradle Installation",
                    state = gradleDistLocalDir,
                    isError = !isGradleDistLocalDirValid,
                    showSnackbar = { message -> scope.launch { snackbarState.showSnackbar(message) } }
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
                        javaHomeDir = javaHome.value.takeIf { it.isNotBlank() },
                        gradleUserHomeDir = gradleUserHome.value.takeIf { it.isNotBlank() },
                        distribution = when (gradleDistSource.value) {
                            GradleDistSource.DEFAULT -> GradleDistribution.Default
                            GradleDistSource.VERSION -> GradleDistribution.Version(gradleDistVersion.value)
                            GradleDistSource.LOCAL -> GradleDistribution.Local(gradleDistLocalDir.value)
                        }
                    )
                )
            },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun GradleDistributionField(
    state: MutableState<GradleDistSource>
) {
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = sourceMenuExpanded,
        onExpandedChange = { sourceMenuExpanded = !sourceMenuExpanded }
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            readOnly = true,
            value = state.value.displayName,
            onValueChange = {},
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceMenuExpanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            label = { Text("Gradle Distribution") }
        )
        ExposedDropdownMenu(
            expanded = sourceMenuExpanded,
            onDismissRequest = { sourceMenuExpanded = false },
        ) {
            GradleDistSource.entries.forEach { source ->
                DropdownMenuItem(
                    text = { Text(source.displayName) },
                    onClick = {
                        state.value = source
                        sourceMenuExpanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun GradleVersionField(
    component: BuildComponent,
    state: MutableState<String>,
    isError: Boolean
) {
    val versions by component.gradleVersions.subscribeAsState()
    var versionMenuExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = versionMenuExpanded,
        onExpandedChange = { versionMenuExpanded = !versionMenuExpanded }
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            value = state.value,
            onValueChange = { state.value = it },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = versionMenuExpanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            label = { Text("Gradle Version") },
            isError = isError
        )
        ExposedDropdownMenu(
            expanded = versionMenuExpanded,
            onDismissRequest = { versionMenuExpanded = false }
        ) {
            versions.forEach { version ->
                DropdownMenuItem(
                    text = { Text(version) },
                    onClick = {
                        state.value = version
                        versionMenuExpanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun DirectoryField(
    description: String,
    state: MutableState<String>,
    defaultState: String? = null,
    readOnly: Boolean = false,
    isError: Boolean = false,
    showHiddenFiles: Boolean = false,
    showSnackbar: (message: String) -> Unit,
) {
    BuildTextField(
        value = state.value,
        onValueChange = { newValue ->
            if (!readOnly) {
                state.value = newValue
            }
        },
        isError = isError,
        label = { Text(description) },
        placeholder = {
            if (defaultState != null) {
                Text(defaultState, color = Color.Gray)
            }
        },
        trailingIcon = {
            val helpText = "Select a $description"
            var isDirChooserOpen by remember { mutableStateOf(false) }
            if (isDirChooserOpen) {
                DirChooserDialog(
                    helpText = helpText,
                    showHiddenFiles = showHiddenFiles,
                    onDirChosen = { dir ->
                        isDirChooserOpen = false
                        if (dir == null) {
                            showSnackbar("No $description selected")
                        } else {
                            state.value = dir.absolutePath
                        }
                    }
                )
            }
            Row {
                IconButton(
                    enabled = state.value.isNotBlank(),
                    onClick = { state.value = "" },
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

@Composable
@Suppress("LongParameterList")
private fun BuildTextField(
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean = false,
    isError: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        readOnly = readOnly,
        onValueChange = onValueChange,
        isError = isError,
        label = label,
        placeholder = placeholder,
        trailingIcon = trailingIcon,
    )
}
