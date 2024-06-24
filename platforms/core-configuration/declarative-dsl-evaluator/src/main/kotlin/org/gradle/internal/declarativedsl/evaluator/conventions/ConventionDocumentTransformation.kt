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

package org.gradle.internal.declarativedsl.evaluator.conventions

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ElementNode
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ElementResolution.SuccessfulElementResolution.ConfiguringElementResolved
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer
import org.gradle.internal.declarativedsl.language.SourceIdentifier


object ConventionDocumentTransformation {

    fun extractConventions(
        document: DeclarativeDocument,
        resolutionContainer: DocumentResolutionContainer,
        usedSoftwareTypes: Set<String>
    ): DeclarativeDocument = object : DeclarativeDocument {
        override val content: Collection<DeclarativeDocument.DocumentNode>
            get() = document.content
                .filter { node -> isTopLevelConventionsCall(node, resolutionContainer) }
                .flatMap { (it as? ElementNode)?.content.orEmpty() }
                .filterIsInstance<ElementNode>()
                .filter { it.name in usedSoftwareTypes }

        override val sourceIdentifier: SourceIdentifier
            get() = document.sourceIdentifier
    }

    private
    fun isTopLevelConventionsCall(
        node: DeclarativeDocument.DocumentNode,
        resolutionContainer: DocumentResolutionContainer
    ) = (node as? ElementNode)?.name == ConventionsTopLevelReceiver::conventions.name &&
        resolutionContainer.data(node).let { resolution ->
            resolution is ConfiguringElementResolved &&
                resolution.elementType.name.qualifiedName == ConventionsConfiguringBlock::class.qualifiedName
        }
}
