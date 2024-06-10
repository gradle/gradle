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
import org.gradle.internal.declarativedsl.dom.DefaultPropertyNode
import org.gradle.internal.declarativedsl.dom.TestApi
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.convertBlockToDocument
import org.gradle.internal.declarativedsl.dom.mutation.DocumentMutation.DocumentNodeTargetedMutation.RemoveNode
import org.gradle.internal.declarativedsl.dom.mutation.NestedScopeSelector.NestedObjectsOfType
import org.gradle.internal.declarativedsl.dom.mutation.ScopeLocationElement.InAllNestedScopes
import org.gradle.internal.declarativedsl.dom.mutation.ScopeLocationElement.InNestedScopes
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer
import org.gradle.internal.declarativedsl.dom.resolution.resolutionContainer
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import kotlin.test.Test
import kotlin.test.assertEquals


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
        val propertyNode = document.elementNamed("nested").propertyNamed("number")
        val newValue = DefaultLiteralNode(
            "789",
            nonsenseSourceData
        )

        val planModelMutations = requestMutation(
            document, resolved,
            ModelMutation.SetPropertyValue(
                schema.property("NestedReceiver", "number"),
                newValue,
                ModelMutation.IfPresentBehavior.Overwrite
            )
        )

        assertEquals(
            listOf(
                DocumentMutation.DocumentNodeTargetedMutation.ReplaceNode(
                    propertyNode,
                    DefaultPropertyNode(propertyNode.name, propertyNode.sourceData, newValue)
                )
            ),
            planModelMutations.documentMutations
        )
    }

    @Test
    fun `unset property`() {
        val planModelMutations = requestMutation(
            document, resolved,
            ModelMutation.UnsetProperty(
                schema.property("TopLevelElement", "number")
            )
        )

        assertEquals(
            listOf(
                RemoveNode(
                    document.elementNamed("addAndConfigure").propertyNamed("number")
                )
            ),
            planModelMutations.documentMutations
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

        val planModelMutations = requestMutation(
            document, resolved,
            ModelMutation.AddElement(
                newElementNode,
                ModelMutation.IfPresentBehavior.FailAndReport
            ),
            ScopeLocation(listOf(InNestedScopes(NestedObjectsOfType(schema.dataClass("NestedReceiver")))))
        )

        assertEquals(
            listOf(
                DocumentMutation.DocumentNodeTargetedMutation.ElementNodeMutation.AddChildrenToEndOfBlock(
                    document.elementNamed("nested"),
                    listOf(newElementNode)
                )
            ),
            planModelMutations.documentMutations
        )
    }

    // TODO: use ReplaceValue instead of ReplaceNode, when possible
    // TODO: unsuccessfulModelMutations related behaviour & tests
    // TODO: set property and there isn't actually one, so it shoudl fail or insert one, depending on the request
    // TODO: mutations in top level blocks

    private
    fun requestMutation(
        document: DeclarativeDocument,
        resolved: DocumentResolutionContainer,
        mutation: ModelMutation,
        scopeLocation: ScopeLocation = ScopeLocation(listOf(InAllNestedScopes))
    ): ModelMutationPlan = planner.planModelMutations(document, resolved,
        ModelMutationRequest(
            scopeLocation,
            mutation
        )
    )

    private
    fun AnalysisSchema.property(dataClassName: String, propertyName: String): DataProperty {
        val dataClass = dataClass(dataClassName)
        val dataProperty = dataClass.properties.first { it.name == propertyName }
        return dataProperty
    }

    private
    fun AnalysisSchema.dataClass(name: String) = dataClassesByFqName.entries.first { it.key.simpleName == name }.value
}
