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
import org.gradle.internal.declarativedsl.dom.TestApi
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.convertBlockToDocument
import org.gradle.internal.declarativedsl.dom.resolution.resolutionContainer
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import kotlin.test.Test
import kotlin.test.assertTrue


class ModelToDocumentMutationPlannerTest {

    private
    val planner = DefaultModelToDocumentMutationPlanner()

    private
    val schema = schemaFromTypes(TestApi.TopLevelReceiver::class, TestApi::class.nestedClasses.toList())

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

    @Test
    fun `xxx`() { // TODO: rename
        val topLevelBlock = ParseTestUtil.parseAsTopLevelBlock(code)

        val document = convertBlockToDocument(topLevelBlock)

        val resolver = tracingCodeResolver()
        resolver.resolve(schema, emptyList(), topLevelBlock)
        val resolved = resolutionContainer(schema, resolver.trace, document)

        println(resolved)

        val planModelMutations = planner.planModelMutations(
            document, resolved, listOf(
                ModelMutationRequest(
                    ScopeLocation(
                        listOf(
                            ScopeLocationElement.InAllNestedScopes
                        )
                    ),
                    ModelMutation.UnsetProperty(
                        schema.property("TopLevelElement", "number")
                    )
                )
            )
        )
        assertTrue(planModelMutations.documentMutations.size == 1) // TODO: better assert
    }

    private
    fun AnalysisSchema.property(dataClassName: String, propertyName: String): DataProperty {
        val dataClass = dataClassesByFqName.entries.first { it.key.simpleName == dataClassName }.value
        val dataProperty = dataClass.properties.first { it.name == propertyName }
        return dataProperty
    }
}
