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

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.internal.declarativedsl.analysis.tracingCodeResolver
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DefaultElementNode
import org.gradle.internal.declarativedsl.dom.DefaultLiteralNode
import org.gradle.internal.declarativedsl.dom.TestApi
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.convertBlockToDocument
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.AddChildrenToEndOfBlock
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.RemoveNode
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.ValueTargetedMutation
import org.gradle.internal.declarativedsl.dom.mutation.ModelMutationFailureReason.ScopeLocationNotMatched
import org.gradle.internal.declarativedsl.dom.mutation.ModelMutationFailureReason.TargetPropertyNotFound
import org.gradle.internal.declarativedsl.dom.mutation.NestedScopeSelector.NestedObjectsOfType
import org.gradle.internal.declarativedsl.dom.mutation.ScopeLocationElement.InNestedScopes
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer
import org.gradle.internal.declarativedsl.dom.resolution.resolutionContainer
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


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
    val document: DeclarativeDocument

    private
    val resolved: DocumentResolutionContainer

    init {
        val topLevelBlock = ParseTestUtil.parseAsTopLevelBlock(code)

        document = convertBlockToDocument(topLevelBlock)

        val resolver = tracingCodeResolver()
        resolver.resolve(schema, emptyList(), topLevelBlock)
        resolved = resolutionContainer(schema, resolver.trace, document)
    }

    private
    val nonsenseSourceData = document.elementNamed("justAdd").sourceData // TODO: in lots of cases it just doesn't make sense to need sourceData as mutation inputs

    @Test
    fun `set property value`() {
        val newValue = DefaultLiteralNode(
            "789",
            nonsenseSourceData
        )

        val mutationPlan = planMutation(
            document, resolved,
            mutationRequest(
                ModelMutation.SetPropertyValue(
                    schema.property("NestedReceiver", "number"),
                    NewValueNodeProvider.Constant(newValue),
                    ModelMutation.IfPresentBehavior.Overwrite
                )
            )
        )

        assertSuccessfulMutation(
            mutationPlan,
            ValueTargetedMutation.ReplaceValue(
                document.elementNamed("nested").propertyNamed("number").value,
                newValue
            )
        )
    }

    @Test
    fun `unset property`() {
        val mutationPlan = planMutation(
            document, resolved,
            mutationRequest(
                ModelMutation.UnsetProperty(
                    schema.property("TopLevelElement", "number")
                )
            )
        )

        assertSuccessfulMutation(
            mutationPlan,
            RemoveNode(document.elementNamed("addAndConfigure").propertyNamed("number"))
        )
    }

    @Test
    fun `add element`() {
        val newElementNode = DefaultElementNode(
            "newAdd",
            nonsenseSourceData,
            listOf(DefaultLiteralNode("yolo", nonsenseSourceData)),
            emptyList()
        )

        val mutationPlan = planMutation(
            document, resolved,
            mutationRequest(
                ModelMutation.AddElement(
                    newElementNode,
                    ModelMutation.IfPresentBehavior.FailAndReport
                ),
                ScopeLocation(listOf(InNestedScopes(NestedObjectsOfType(schema.dataClass("NestedReceiver")))))
            )
        )

        assertSuccessfulMutation(
            mutationPlan,
            AddChildrenToEndOfBlock(document.elementNamed("nested"), listOf(newElementNode))
        )
    }

    @Test
    fun `property not found`() {
        val request = mutationRequest(
            ModelMutation.SetPropertyValue(
                schema.property("NestedReceiver", "number"),
                NewValueNodeProvider.Constant(DefaultLiteralNode("789", nonsenseSourceData)),
                ModelMutation.IfPresentBehavior.Overwrite
            ),
            ScopeLocation(listOf(InNestedScopes(NestedObjectsOfType(schema.dataClass("TopLevelElement")))))
        )
        val mutationPlan = planMutation(document, resolved, request)

        assertFailedMutation(
            mutationPlan,
            UnsuccessfulModelMutation(request, listOf(TargetPropertyNotFound))
        )
    }

    @Test
    fun `no matching scope`() {
        val invalidScopeLocation = ScopeLocation(
            listOf(
                InNestedScopes(NestedObjectsOfType(schema.dataClass("NestedReceiver"))),
                InNestedScopes(NestedObjectsOfType(schema.dataClass("TopLevelElement")))
            )
        )

        val request = mutationRequest(
            ModelMutation.AddElement(
                DefaultElementNode(
                    "newAdd",
                    nonsenseSourceData,
                    listOf(DefaultLiteralNode("yolo", nonsenseSourceData)),
                    emptyList()
                ),
                ModelMutation.IfPresentBehavior.FailAndReport
            ),
            invalidScopeLocation
        )
        val mutationPlan = planMutation(
            document, resolved,
            request
        )

        assertFailedMutation(
            mutationPlan,
            UnsuccessfulModelMutation(request, listOf(ScopeLocationNotMatched))
        )
    }

    private
    fun mutationRequest(
        mutation: ModelMutation,
        scopeLocation: ScopeLocation = ScopeLocation(listOf(ScopeLocationElement.InAllNestedScopes))
    ) = ModelMutationRequest(scopeLocation, mutation)

    // TODO: set property and there isn't actually one, so it shoudl fail or insert one, depending on the request
    // TODO: mutations in top level blocks

    private
    fun planMutation(
        document: DeclarativeDocument,
        resolved: DocumentResolutionContainer,
        mutationRequest: ModelMutationRequest
    ): ModelMutationPlan = planner.planModelMutation(document, resolved, mutationRequest, mutationArguments { })

    private
    fun assertSuccessfulMutation(planModelMutations: ModelMutationPlan, expectedDocumentMutation: DocumentMutation) {
        assertEquals(
            listOf(expectedDocumentMutation),
            planModelMutations.documentMutations
        )
        assertTrue { planModelMutations.unsuccessfulModelMutations.isEmpty() }
    }

    private
    fun assertFailedMutation(planModelMutations: ModelMutationPlan, expectedFailure: UnsuccessfulModelMutation) {
        assertTrue { planModelMutations.documentMutations.isEmpty() }
        assertEquals(
            listOf(expectedFailure),
            planModelMutations.unsuccessfulModelMutations
        )
    }

    private
    fun AnalysisSchema.property(dataClassName: String, propertyName: String): DataProperty {
        val dataClass = dataClass(dataClassName)
        val dataProperty = dataClass.properties.first { it.name == propertyName }
        return dataProperty
    }

    private
    fun AnalysisSchema.dataClass(name: String) = dataClassesByFqName.entries.first { it.key.simpleName == name }.value
}
