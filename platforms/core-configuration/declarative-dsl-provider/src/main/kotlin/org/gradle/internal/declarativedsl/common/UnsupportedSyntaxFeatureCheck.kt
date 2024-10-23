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

package org.gradle.internal.declarativedsl.common

import org.gradle.declarative.dsl.evaluation.InterpretationStepFeature
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.UnsupportedSyntax
import org.gradle.internal.declarativedsl.dom.data.NodeData
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer
import org.gradle.internal.declarativedsl.evaluator.checks.DocumentCheck
import org.gradle.internal.declarativedsl.evaluator.checks.DocumentCheckFailure
import org.gradle.internal.declarativedsl.evaluator.checks.DocumentCheckFailureLocation
import org.gradle.internal.declarativedsl.evaluator.checks.DocumentCheckFailureReason.UnsupportedSyntaxInDocument
import org.gradle.internal.declarativedsl.ignoreSomeNonDeclarativeSyntaxWeCurrentlyHaveNoSolutionFor
import java.io.Serializable

internal
object UnsupportedSyntaxFeatureCheck : DocumentCheck {

    val feature = UnsupportedSyntaxFeature()

    class UnsupportedSyntaxFeature : InterpretationStepFeature.DocumentChecks, Serializable {
        override val checkKeys: Iterable<String> = listOf(checkKey)
    }

    override val checkKey: String
        get() = UnsupportedSyntaxFeatureCheck::class.java.name

    override fun detectFailures(document: DeclarativeDocument, resolutionContainer: DocumentResolutionContainer, isAnalyzedNode: NodeData<Boolean>): List<DocumentCheckFailure> {
        val failures = mutableListOf<DocumentCheckFailure>()

        fun visitNode(node: DeclarativeDocument.DocumentNode) {
            when {
                !isAnalyzedNode.data(node) -> Unit

                node is DeclarativeDocument.DocumentNode.PropertyNode -> Unit

                node is DeclarativeDocument.DocumentNode.ElementNode -> {
                    if (node.content.isNotEmpty()) {
                        node.content.forEach {
                            visitNode(it)
                        }
                    }
                }

                node is DeclarativeDocument.DocumentNode.ErrorNode -> {
                    node.errors
                        .filterIsInstance<UnsupportedSyntax>()
                        .filter(ignoreSomeNonDeclarativeSyntaxWeCurrentlyHaveNoSolutionFor)
                        .forEach {
                            failures.add(DocumentCheckFailure(this, DocumentCheckFailureLocation.FailedAtNode(node), UnsupportedSyntaxInDocument(it.cause)))
                        }
                }
            }
        }

        document.content.forEach {
            visitNode(it)
        }

        return failures
    }
}
