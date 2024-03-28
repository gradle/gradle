package org.gradle.client.ui.connected

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.gradle.client.ui.composables.BackIcon
import org.gradle.client.ui.theme.plusPaneSpacing

@Composable
fun ConnectedContent(component: ConnectedComponent) {
    Scaffold(
        topBar = { TopBar(component) }
    ) { scaffoldPadding ->
        Surface(modifier = Modifier.padding(scaffoldPadding.plusPaneSpacing())) {
            val model by component.model.subscribeAsState()
            when (val current = model) {
                ConnectedModel.Disconnected -> DisconnectedMainContent(component)
                ConnectedModel.Connected -> ConnectedMainContent(component, current)
            }
        }
    }
}

@Composable
private fun DisconnectedMainContent(component: ConnectedComponent) {
    Column {
        Text("Disconnected")
        Text(component.gradleConnectionParameters.toString())
    }
}

@Composable
private fun ConnectedMainContent(component: ConnectedComponent, current: ConnectedModel) {
    Column {
        Text("Connected")
        Text(component.gradleConnectionParameters.toString())
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
                    ConnectedModel.Disconnected -> Text("Disconnected")
                    ConnectedModel.Connected -> Text("Connected")
                }
            }
        }
    )
}