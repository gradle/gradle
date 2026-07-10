/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.declarativedsl.evaluator.checks

import org.gradle.declarative.dsl.evaluation.InterpretationStepFeature
import org.gradle.internal.declarativedsl.analysis.ResolutionTrace
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.UnsupportedSyntaxCause
import org.gradle.internal.declarativedsl.dom.data.NodeData
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.LanguageTreeMappingContainer
import org.gradle.internal.declarativedsl.dom.resolution.DocumentWithResolution
import org.gradle.internal.declarativedsl.evaluator.features.InterpretationStepFeatureHandler
import org.gradle.internal.declarativedsl.language.SourceData


/**
 * An interface for document checks running on a [DocumentWithResolution], detected and reported from [detectFailures].
 * The [checkKey] in the implementations should be unique across all checks used by an evaluator.
 *
 * The implementations may optionally also implement [DocumentLowLevelResolutionCheck] to run lower-level checks on the resolution results.
 */
interface DocumentCheck : InterpretationStepFeatureHandler<InterpretationStepFeature.DocumentChecks> {
    val checkKey: String

    override fun shouldHandleFeature(feature: InterpretationStepFeature.DocumentChecks): Boolean =
        checkKey in feature.checkKeys

    fun detectFailures(documentWithResolution: DocumentWithResolution, isAnalyzedNode: NodeData<Boolean>): List<DocumentCheckFailure>
}

/**
 * An additional interface that may be implemented by a [DocumentCheck] implementation to run advanced checks on the low-level resolution
 * results instead of the DOM resolution.
 */
interface DocumentLowLevelResolutionCheck {
    fun detectFailuresInLowLevelResolution(
        document: DeclarativeDocument,
        languageTreeMappingContainer: LanguageTreeMappingContainer,
        resolutionTrace: ResolutionTrace
    ): List<DocumentCheckFailure>
}


data class DocumentCheckFailure(
    val check: DocumentCheck,
    val location: DocumentCheckFailureLocation,
    val reason: DocumentCheckFailureReason
)


sealed interface DocumentCheckFailureLocation {
    val node: DeclarativeDocument.Node

    val sourceData: SourceData
        get() = when (this) {
            is FailedAtNode -> node.sourceData
            is FailedAtValue -> node.sourceData
        }

    data class FailedAtNode(override val node: DeclarativeDocument.DocumentNode) : DocumentCheckFailureLocation
    data class FailedAtValue(override val node: DeclarativeDocument.ValueNode) : DocumentCheckFailureLocation
}


sealed interface DocumentCheckFailureReason {
    data object PluginManagementBlockOrderViolated : DocumentCheckFailureReason
    data object PluginsBlockOrderViolated : DocumentCheckFailureReason
    data object DuplicatePluginsBlock : DocumentCheckFailureReason
    data object DuplicatePluginManagementBlock : DocumentCheckFailureReason
    data object AccessOnCurrentReceiverViolation : DocumentCheckFailureReason
    data class UnsupportedSyntaxInDocument(val cause : UnsupportedSyntaxCause) : DocumentCheckFailureReason
}
