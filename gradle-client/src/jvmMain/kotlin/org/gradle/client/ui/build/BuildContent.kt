package org.gradle.client.ui.build

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BuildContent(component: BuildComponent) {
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(0.dp).height(56.dp).fillMaxWidth(),
                navigationIcon = {
                    Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = { component.onCloseClicked() }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "")
                        }
                    }
                },
                title = {
                    Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Build #${component.id}")
                    }
                }
            )
        }
    ) { scaffoldPadding ->
        Surface(modifier = Modifier.padding(scaffoldPadding)) {
            Text("BUILD #${component.id}")
        }
    }
}
