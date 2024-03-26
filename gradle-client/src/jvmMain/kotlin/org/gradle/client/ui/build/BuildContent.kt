package org.gradle.client.ui.build

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.gradle.client.ui.composables.BackIcon
import org.gradle.client.ui.composables.Loading
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

@Composable
private fun BuildMainContent(component: BuildComponent, model: BuildModel.Loaded) {
    Text(model.build.rootDir.absolutePath)
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