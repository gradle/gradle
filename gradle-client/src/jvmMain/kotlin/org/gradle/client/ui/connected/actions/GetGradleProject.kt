package org.gradle.client.ui.connected.actions

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.gradle.tooling.model.GradleProject

class GetGradleProject : GetModelAction<GradleProject> {

    override val modelType = GradleProject::class

    @Composable
    override fun ColumnScope.ModelContent(model: GradleProject) {
        Text(
            text = "Gradle Project",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Root Project Identifier: ${model.projectIdentifier}",
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = "Root Project Name: ${model.name}",
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = "Root Project Description: ${model.description}",
            style = MaterialTheme.typography.labelSmall
        )
    }
}