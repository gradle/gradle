package org.gradle.client.ui.welcome

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.AwtWindow
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun WelcomeContent(component: WelcomeComponent) {
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(0.dp).height(56.dp).fillMaxWidth(),
                title = {
                    Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Gradle Client")
                    }
                }
            )
        },
        floatingActionButton = {
            var isDirChooserOpen by remember { mutableStateOf(false) }
            if (isDirChooserOpen) {
                BuildChooserDialog(
                    onBuildChosen = { rootDir ->
                        isDirChooserOpen = false
                        if (rootDir != null) {
                            component.onAddBuildClicked(rootDir)
                        }
                    }
                )
            }
            ExtendedFloatingActionButton(
                icon = { Icon(Icons.Default.Add, "") },
                text = { Text("Add build") },
                onClick = { isDirChooserOpen = true },
            )
        }
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier.padding(scaffoldPadding),
        ) {
            repeat(100) { number ->
                item {
                    ListItem(
                        modifier = Modifier.selectable(
                            selected = false,
                            onClick = {
                                component.onBuildClicked(number.toString())
                            }
                        ),
                        leadingContent = {
                            Icon(
                                modifier = Modifier.size(36.dp),
                                painter = painterResource(resourcePath = "/icons/icon_gradle_rgb.png"),
                                contentDescription = "Gradle Build"
                            )
                        },
                        headlineContent = { Text("gradle #$number") },
                        supportingContent = { Text("/Users/paul/src/gradle-related/gradle") },
                        trailingContent = {
                            IconButton(
                                onClick = {},
                                content = { Icon(Icons.Default.Close, "") }
                            )
                        }
                    )
                }
            }
        }
    }
}


@Composable
private fun BuildChooserDialog(
    parent: Frame? = null,
    onBuildChosen: (buildRootDir: File?) -> Unit
) = AwtWindow(
    create = {
        object : FileDialog(parent, "Choose a Gradle settings script", LOAD) {
            init {
                setFilenameFilter { dir, name ->
                    dir.resolve(name).let { it.isFile && it.name.startsWith("settings.gradle") }
                }
            }

            override fun setVisible(value: Boolean) {
                super.setVisible(value)
                if (value) {
                    onBuildChosen(directory?.let(::File))
                }
            }
        }
    },
    dispose = FileDialog::dispose
)
