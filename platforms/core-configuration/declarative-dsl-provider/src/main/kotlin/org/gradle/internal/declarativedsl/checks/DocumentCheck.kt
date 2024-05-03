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

package org.gradle.internal.declarativedsl.checks

import org.gradle.internal.declarativedsl.dom.ResolvedDeclarativeDocument
import org.gradle.internal.declarativedsl.language.SourceData


internal
interface DocumentCheck {
    fun detectFailures(resolvedDeclarativeDocument: ResolvedDeclarativeDocument): List<DocumentCheckFailure>
}


internal
data class DocumentCheckFailure(
    val check: DocumentCheck,
    val location: DocumentCheckFailureLocation,
    val reason: DocumentCheckFailureReason
)


internal
sealed interface DocumentCheckFailureLocation {
    val sourceData: SourceData
        get() = when (this) {
            is FailedAtNode -> node.sourceData
            is FailedAtValue -> node.sourceData
        }

    data class FailedAtNode(val node: ResolvedDeclarativeDocument.ResolvedDocumentNode) : DocumentCheckFailureLocation
    data class FailedAtValue(val node: ResolvedDeclarativeDocument.ResolvedValueNode) : DocumentCheckFailureLocation
}


internal
sealed interface DocumentCheckFailureReason {
    object PluginManagementBlockOrderViolated : DocumentCheckFailureReason
    object PluginsBlockOrderViolated : DocumentCheckFailureReason
    object DuplicatePluginsBlock : DocumentCheckFailureReason
    object DuplicatePluginManagementBlock : DocumentCheckFailureReason
}
