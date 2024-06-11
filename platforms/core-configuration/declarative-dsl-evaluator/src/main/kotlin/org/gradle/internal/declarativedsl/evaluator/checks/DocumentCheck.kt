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
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer
import org.gradle.internal.declarativedsl.evaluator.features.InterpretationStepFeatureHandler
import org.gradle.internal.declarativedsl.language.SourceData


interface DocumentCheck : InterpretationStepFeatureHandler<InterpretationStepFeature.DocumentChecks> {
    val checkKey: String

    override fun shouldHandleFeature(feature: InterpretationStepFeature.DocumentChecks): Boolean =
        checkKey in feature.checkKeys

    fun detectFailures(document: DeclarativeDocument, resolutionContainer: DocumentResolutionContainer): List<DocumentCheckFailure>
}


data class DocumentCheckFailure(
    val check: DocumentCheck,
    val location: DocumentCheckFailureLocation,
    val reason: DocumentCheckFailureReason
)


sealed interface DocumentCheckFailureLocation {
    val sourceData: SourceData
        get() = when (this) {
            is FailedAtNode -> node.sourceData
            is FailedAtValue -> node.sourceData
        }

    data class FailedAtNode(val node: DeclarativeDocument.DocumentNode) : DocumentCheckFailureLocation
    data class FailedAtValue(val node: DeclarativeDocument.ValueNode) : DocumentCheckFailureLocation
}


sealed interface DocumentCheckFailureReason {
    object PluginManagementBlockOrderViolated : DocumentCheckFailureReason
    object PluginsBlockOrderViolated : DocumentCheckFailureReason
    object DuplicatePluginsBlock : DocumentCheckFailureReason
    object DuplicatePluginManagementBlock : DocumentCheckFailureReason
}
