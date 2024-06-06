package org.gradle.client.core.gradle.dcl

import org.gradle.client.build.model.ResolvedDomPrerequisites
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ElementResolution.ElementNotResolved
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ElementResolution.SuccessfulElementResolution.ConfiguringElementResolved
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ElementResolution.SuccessfulElementResolution.ContainerElementResolved
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer
import org.gradle.internal.declarativedsl.evaluator.main.AnalysisSequenceResult
import org.gradle.internal.declarativedsl.evaluator.main.SimpleAnalysisEvaluator
import org.gradle.internal.declarativedsl.evaluator.runner.stepResultOrPartialResult
import org.gradle.internal.declarativedsl.language.SourceIdentifier

fun analyzer(
    prerequisites: ResolvedDomPrerequisites
): SimpleAnalysisEvaluator =
    SimpleAnalysisEvaluator.withSchema(
        prerequisites.settingsInterpretationSequence,
        prerequisites.projectInterpretationSequence
    )

fun AnalysisSequenceResult.sourceIdentifier(): SourceIdentifier =
    stepResults.values.last().stepResultOrPartialResult.languageTreeResult.topLevelBlock.sourceData.sourceIdentifier

fun DeclarativeDocument.DocumentNode.ElementNode.type(resolutionContainer: DocumentResolutionContainer): DataType? =
    when (val resolution = resolutionContainer.data(this)) {
        is ElementNotResolved -> null
        is ConfiguringElementResolved -> resolution.elementType
        is ContainerElementResolved -> resolution.elementType
    }
