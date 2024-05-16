package org.gradle.client.ui.connected.actions

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.gradle.client.build.action.GetResolvedDomAction
import org.gradle.client.build.model.ResolvedDomPrerequisites
import org.gradle.internal.declarativedsl.analysis.analyzeEverything
import org.gradle.internal.declarativedsl.dom.resolvedDocument
import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.gradle.internal.declarativedsl.parsing.DefaultLanguageTreeBuilder
import org.gradle.internal.declarativedsl.parsing.parse
import org.gradle.tooling.BuildAction
import java.io.File

class GetResolvedDom : GetModelAction.GetCompositeModelAction<ResolvedDomPrerequisites> {

    override val modelType = ResolvedDomPrerequisites::class

    override val buildAction: BuildAction<ResolvedDomPrerequisites> = GetResolvedDomAction()

    @Composable
    override fun ColumnScope.ModelContent(model: ResolvedDomPrerequisites) {
        val buildFile = File(model.buildFilePath)
        val buildFileContent = buildFile.readText()
        val parsedLightTree = parse(buildFileContent)
        val languageTreeResult = DefaultLanguageTreeBuilder().build(parsedLightTree, SourceIdentifier(buildFile.name))

        val resolvedDocument = resolvedDocument(model.analysisSchema, languageTreeResult, analyzeEverything, true)

        Text(
            text = "Gradle Resolved DOM",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Schema: $resolvedDocument",
            // todo: this is just a useless println, but the content is there, can be seen in the debugger
            style = MaterialTheme.typography.labelSmall
        )
    }
}
