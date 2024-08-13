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

package org.gradle.internal.declarativedsl.dom.mutation

import org.gradle.internal.declarativedsl.dom.DefaultElementNode
import org.gradle.internal.declarativedsl.dom.DefaultLiteralNode
import org.gradle.internal.declarativedsl.dom.TestApi
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.AddChildrenToEndOfBlock
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.RemoveNode
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.ValueTargetedMutation.ReplaceValue
import org.gradle.internal.declarativedsl.dom.mutation.ModelMutationIssueReason.ScopeLocationNotMatched
import org.gradle.internal.declarativedsl.dom.mutation.common.NewDocumentNodes
import org.gradle.internal.declarativedsl.dom.mutation.common.NodeRepresentationFlagsContainer
import org.gradle.internal.declarativedsl.dom.resolution.DocumentWithResolution
import org.gradle.internal.declarativedsl.dom.resolution.documentWithResolution
import org.gradle.internal.declarativedsl.language.SyntheticallyProduced
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.gradle.internal.declarativedsl.schemaUtils.functionFor
import org.gradle.internal.declarativedsl.schemaUtils.propertyFor
import org.gradle.internal.declarativedsl.schemaUtils.typeFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.Test


class ModelToDocumentMutationPlannerTest {

    private
    val code =
        """
            addAndConfigure("test") {
                number = 123
            }
            justAdd("test2")
            complexValueOne = one(two("three"))
            complexValueOneFromUtils = utils.oneUtil()
            complexValueTwo = two("three")
            nested {
                number = 456
                add()
            }
        """.trimIndent()

    private
    val planner = DefaultModelToDocumentMutationPlanner()

    private
    val schema = schemaFromTypes(TestApi.TopLevelReceiver::class, TestApi::class.nestedClasses.toList())

    private
    val resolved: DocumentWithResolution = documentWithResolution(schema, ParseTestUtil.parse(code))

    private
    val document = resolved.document

    @Test
    fun `set property value`() {
        val newValue = DefaultLiteralNode(
            "789",
            SyntheticallyProduced
        )

        val mutationPlan = planMutation(
            resolved,
            mutationRequest(
                ModelMutation.SetPropertyValue(
                    schema.propertyFor(TestApi.NestedReceiver::number),
                    NewValueNodeProvider.Constant(newValue)
                )
            )
        )

        assertSuccessfulMutation(
            mutationPlan,
            ReplaceValue(document.elementNamed("nested").propertyNamed("number").value) { newValue }
        )
    }

    @Test
    fun `unset property`() {
        val mutationPlan = planMutation(
            resolved,
            mutationRequest(
                ModelMutation.UnsetProperty(
                    schema.propertyFor(TestApi.TopLevelElement::number)
                )
            )
        )

        assertSuccessfulMutation(
            mutationPlan,
            RemoveNode(document.elementNamed("addAndConfigure").propertyNamed("number"))
        )
    }

    @Test
    fun `add configuring element if absent - when present`() {
        val nestedConfigureElementPresent = documentWithResolution(
            schema, ParseTestUtil.parse(
                """
                nested {
                    number = 456
                    add()
                    configure { } // <- it's already here
                }
                """.trimIndent()
            )
        )

        val mutationPlan = planMutation(nestedConfigureElementPresent, addNestedConfigureBlock())

        // Expected to do nothing:
        assertEquals(emptyList<DocumentMutation>(), mutationPlan.documentMutations)
        assertEquals(emptyList<ModelMutationIssue>(), mutationPlan.modelMutationIssues)
    }

    @Test
    fun `add configuring element if absent - when absent`() {
        val mutationPlan = planMutation(
            resolved,
            addNestedConfigureBlock()
        )

        // Expected to insert a configuring block:
        assertSuccessfulMutation(
            mutationPlan,
            AddChildrenToEndOfBlock(document.elementNamed("nested")) {
                val element = DefaultElementNode("configure", SyntheticallyProduced, emptyList(), emptyList())
                NewDocumentNodes(
                    listOf(element),
                    NodeRepresentationFlagsContainer(forceEmptyBlockForNodes = setOf(element))
                )
            }
        )
    }

    private
    fun addNestedConfigureBlock() = mutationRequest(
        ModelMutation.AddConfiguringBlockIfAbsent(schema.functionFor(TestApi.NestedReceiver::configure)),
        ScopeLocation.fromTopLevel().inObjectsOfType(schema.typeFor<TestApi.NestedReceiver>())
    )

    @Test
    fun `add element`() {
        val newElementNode = DefaultElementNode(
            "newAdd",
            SyntheticallyProduced,
            listOf(DefaultLiteralNode("yolo", SyntheticallyProduced)),
            emptyList()
        )

        val mutationPlan = planMutation(
            resolved,
            mutationRequest(
                ModelMutation.AddNewElement(
                    NewElementNodeProvider.Constant(newElementNode),
                ),
                ScopeLocation.fromTopLevel().inObjectsOfType(schema.typeFor<TestApi.NestedReceiver>())
            )
        )

        assertSuccessfulMutation(
            mutationPlan,
            AddChildrenToEndOfBlock(document.elementNamed("nested")) {
                NewDocumentNodes(listOf(newElementNode))
            }
        )
    }

    @Test
    fun `property not found`() {
        val request = mutationRequest(
            ModelMutation.SetPropertyValue(
                schema.propertyFor(TestApi.NestedReceiver::number),
                NewValueNodeProvider.Constant(DefaultLiteralNode("789", SyntheticallyProduced))
            ),
            ScopeLocation.fromTopLevel().inObjectsOfType(schema.typeFor<TestApi.TopLevelElement>())
        )
        val mutationPlan = planMutation(resolved, request)

        assertFailedMutation(
            mutationPlan,
            ModelMutationIssue(ModelMutationIssueReason.TargetPropertyNotFound)
        )
    }

    @Test
    fun `no matching scope`() {
        val invalidScopeLocation = ScopeLocation.fromTopLevel()
            .inObjectsOfType(schema.typeFor<TestApi.NestedReceiver>())
            .inObjectsOfType(schema.typeFor<TestApi.TopLevelElement>())

        val request = mutationRequest(
            ModelMutation.AddNewElement(
                NewElementNodeProvider.Constant(
                    DefaultElementNode(
                        "newAdd",
                        SyntheticallyProduced,
                        listOf(DefaultLiteralNode("yolo", SyntheticallyProduced)),
                        emptyList()
                    )
                ),
            ),
            invalidScopeLocation
        )
        val mutationPlan = planMutation(resolved, request)

        assertFailedMutation(
            mutationPlan,
            ModelMutationIssue(ScopeLocationNotMatched)
        )
    }

    private
    fun mutationRequest(
        mutation: ModelMutation,
        scopeLocation: ScopeLocation = ScopeLocation.fromTopLevel().alsoInNestedScopes()
    ) = ModelMutationRequest(scopeLocation, mutation)

    // TODO: set property and there isn't actually one, so it should fail or insert one, depending on the request
    // TODO: mutations in top level blocks

    private
    fun planMutation(
        documentWithResolution: DocumentWithResolution,
        mutationRequest: ModelMutationRequest
    ): ModelMutationPlan = planner.planModelMutation(schema, documentWithResolution, mutationRequest, mutationArguments { })

    private
    fun assertSuccessfulMutation(planModelMutations: ModelMutationPlan, expectedDocumentMutation: DocumentMutation) {
        assertTrue(isEquivalentMutation(expectedDocumentMutation, planModelMutations.documentMutations.single()))
        assertTrue { planModelMutations.modelMutationIssues.isEmpty() }
    }

    private
    fun isEquivalentMutation(expected: DocumentMutation, actual: DocumentMutation) = when (expected) {
        is AddChildrenToEndOfBlock -> actual is AddChildrenToEndOfBlock &&
            expected.targetNode == actual.targetNode &&
            expected.nodes().nodes == actual.nodes().nodes &&
            expected.nodes().representationFlags == actual.nodes().representationFlags
        is ReplaceValue -> actual is ReplaceValue && expected.targetValue == actual.targetValue && expected.replaceWithValue() == actual.replaceWithValue()
        is RemoveNode -> expected == actual
        else -> throw UnsupportedOperationException("cannot check for the expected mutation $expected")
    }

    private
    fun assertFailedMutation(planModelMutations: ModelMutationPlan, expectedFailure: ModelMutationIssue) {
        assertTrue { planModelMutations.documentMutations.isEmpty() }
        assertEquals(
            listOf(expectedFailure),
            planModelMutations.modelMutationIssues
        )
    }
}
