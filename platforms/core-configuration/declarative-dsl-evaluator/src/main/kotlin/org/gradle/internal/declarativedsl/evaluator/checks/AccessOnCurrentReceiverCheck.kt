/*
 * Copyright 2025 the original author or authors.
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
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.analysis.AssignmentRecord
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.ResolutionTrace
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.data.NodeData
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.LanguageTreeMappingContainer
import org.gradle.internal.declarativedsl.dom.resolution.DocumentWithResolution

object AccessOnCurrentReceiverCheck : DocumentCheck, DocumentLowLevelResolutionCheck {
    val feature = AccessOnCurrentReceiverCheckFeature()

    class AccessOnCurrentReceiverCheckFeature : InterpretationStepFeature.DocumentChecks {
        override val checkKeys: Iterable<String>
            get() = listOf(checkKey)
    }

    override val checkKey: String
        get() = javaClass.name

    override fun detectFailures(
        documentWithResolution: DocumentWithResolution,
        isAnalyzedNode: NodeData<Boolean>
    ): List<DocumentCheckFailure> = emptyList()

    override fun detectFailuresInLowLevelResolution(
        document: DeclarativeDocument,
        languageTreeMappingContainer: LanguageTreeMappingContainer,
        resolutionTrace: ResolutionTrace
    ): List<DocumentCheckFailure> = CheckContext(resolutionTrace, languageTreeMappingContainer).run {
        document.content.forEach(::visitNode)
        failures
    }

    private class CheckContext(val resolutionTrace: ResolutionTrace, val languageTreeMappingContainer: LanguageTreeMappingContainer) {
        val failures = mutableListOf<DocumentCheckFailure>()

        fun visitNode(node: DeclarativeDocument.Node) {
            when (node) {
                is DeclarativeDocument.DocumentNode.PropertyNode -> {
                    when (val record = assignmentRecord(node)) {
                        is ResolutionTrace.ResolutionOrErrors.Resolution -> {
                            if (isViolatingAssignmentLhs(record.result))
                                report(node)
                        }

                        else -> Unit
                    }
                    visitNode(node.value)
                }

                is DeclarativeDocument.ValueNode.NamedReferenceNode -> {
                    val resolution = valueOrigin(node)
                    if (resolution is ResolutionTrace.ResolutionOrErrors.Resolution && isViolatingOrigin(resolution.result)) {
                        report(node)
                    }
                }

                is DeclarativeDocument.ValueNode.ValueFactoryNode -> {
                    val resolution = valueOrigin(node)
                    if (resolution is ResolutionTrace.ResolutionOrErrors.Resolution && isViolatingOrigin(resolution.result)) {
                        report(node)
                    }
                    node.values.forEach(::visitNode)
                }

                is DeclarativeDocument.DocumentNode.ElementNode -> {
                    val resolution = resolutionTrace.expressionResolution(languageTreeMappingContainer.data(node))
                    if (resolution is ResolutionTrace.ResolutionOrErrors.Resolution && isViolatingOrigin(resolution.result)) {
                        report(node)
                    }
                    node.elementValues.forEach(::visitNode)
                    node.content.forEach(::visitNode)
                }

                is DeclarativeDocument.DocumentNode.ErrorNode,
                is DeclarativeDocument.ValueNode.LiteralValueNode -> Unit
            }
        }

        private fun isUsageOfDirectOnlyAccessMember(objectOrigin: ObjectOrigin.HasReceiver): Boolean = when (objectOrigin) { // is direct-access-only?
            is ObjectOrigin.BuilderReturnedReceiver -> (objectOrigin.function as? SchemaMemberFunction)?.isDirectAccessOnly == true
            is ObjectOrigin.NewObjectFromMemberFunction -> objectOrigin.function.isDirectAccessOnly
            is ObjectOrigin.PropertyReference -> objectOrigin.property.isDirectAccessOnly
            is ObjectOrigin.CustomConfigureAccessor,
            is ObjectOrigin.PropertyDefaultValue,
            is ObjectOrigin.ConfiguringLambdaReceiver -> false
        }

        fun isViolatingOrigin(objectOrigin: ObjectOrigin): Boolean =
            if (objectOrigin is ObjectOrigin.HasReceiver) {
                val isNotCurrentReceiver = objectOrigin.receiver.let { it !is ObjectOrigin.ImplicitThisReceiver || !it.isCurrentScopeReceiver }
                isNotCurrentReceiver && isUsageOfDirectOnlyAccessMember(objectOrigin)
            } else false

        fun isViolatingAssignmentLhs(assignmentRecord: AssignmentRecord) =
            assignmentRecord.lhs.property.isDirectAccessOnly &&
                assignmentRecord.lhs.receiverObject.let { lhsReceiver -> lhsReceiver is ObjectOrigin.ImplicitThisReceiver && !lhsReceiver.isCurrentScopeReceiver }

        private fun valueOrigin(valueNode: DeclarativeDocument.ValueNode) = resolutionTrace.expressionResolution(languageTreeMappingContainer.data(valueNode))

        private fun assignmentRecord(propertyNode: DeclarativeDocument.DocumentNode.PropertyNode) = resolutionTrace.assignmentResolution(languageTreeMappingContainer.data(propertyNode))

        fun report(node: DeclarativeDocument.Node) {
            val location = when (node) {
                is DeclarativeDocument.DocumentNode -> DocumentCheckFailureLocation.FailedAtNode(node)
                is DeclarativeDocument.ValueNode -> DocumentCheckFailureLocation.FailedAtValue(node)
            }
            failures.add(DocumentCheckFailure(AccessOnCurrentReceiverCheck, location, DocumentCheckFailureReason.AccessOnCurrentReceiverViolation))
        }
    }
}
