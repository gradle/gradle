package org.gradle.client.ui.connected.actions

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.gradle.tooling.model.gradle.GradleBuild

class GetGradleBuild : GetModelAction<GradleBuild> {

    override val modelType = GradleBuild::class

    @Composable
    override fun ColumnScope.ModelContent(model: GradleBuild) {
        Text(
            text = "Gradle Build",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Build Identifier: ${model.buildIdentifier.rootDir}",
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = "Root Project Identifier: ${model.rootProject.projectIdentifier}",
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = "Root Project Name: ${model.rootProject.name}",
            style = MaterialTheme.typography.labelSmall
        )
    }
}
