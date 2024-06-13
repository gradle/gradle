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

import org.gradle.declarative.dsl.evaluation.EvaluationSchema
import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.analysis.DefaultOperationGenerationId
import org.gradle.internal.declarativedsl.analysis.analyzeEverything
import org.gradle.internal.declarativedsl.dom.mutation.ModelMutation.IfPresentBehavior.Ignore
import org.gradle.internal.declarativedsl.dom.resolution.documentWithResolution
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.gradle.internal.declarativedsl.schemaUtils.findFunctionFor
import org.gradle.internal.declarativedsl.schemaUtils.findPropertyFor
import org.gradle.internal.declarativedsl.schemaUtils.findTypeFor
import org.gradle.internal.declarativedsl.schemaUtils.functionFor
import org.gradle.internal.declarativedsl.schemaUtils.propertyFor
import org.gradle.internal.declarativedsl.schemaUtils.typeFor
import kotlin.test.Test
import kotlin.test.assertEquals


object MutationAsTextRunnerTest {

    @Test
    fun `can run a mutation from a mutation definition`() {
        val languageTree = ParseTestUtil.parse(
            """
            nestedOne {
                x = 0
                // I can comment this
                nestedTwo {
                    y = 0
                }
            }
            """.trimIndent()
        )
        val document = documentWithResolution(schema.analysisSchema, languageTree, DefaultOperationGenerationId.finalEvaluation, analyzeEverything)

        val result = runner.runMutation(
            mutationDefinition,
            mutationArguments {
                argument(mutationDefinition.xParam, 1)
                argument(mutationDefinition.yParam, 2)
            },
            TextMutationApplicationTarget(document, schema)
        )
        assertEquals(
            """
            nestedOne {
                x = 1
                // I can comment this
                nestedTwo {
                    y = 2
                }
            }

            """.trimIndent(),
            (result.stepResults.last() as ModelMutationStepResult.ModelMutationStepApplied).newDocumentText
        )
    }

    private
    val runner = MutationAsTextRunner()
}


private
val mutationDefinition = object : MutationDefinition {
    val xParam = MutationParameter("x", "new value for x", MutationParameterKind.IntParameter)
    val yParam = MutationParameter("y", "new value for y", MutationParameterKind.IntParameter)

    override val id: String = "com.example.mutation"
    override val name: String = "Example mutation"
    override val description: String = "Set the new values for x and y based on the parameters"
    override val parameters: List<MutationParameter<*>> = emptyList()

    override fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean = with(projectAnalysisSchema) {
        findTypeFor<TopLevelReceiverForMutations>() != null &&
            findPropertyFor(NestedOne::x) != null &&
            findFunctionFor(NestedOne::nestedTwo) != null &&
            findPropertyFor(NestedTwo::y) != null
    }


    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> = with(projectAnalysisSchema) {
        val nestedOneX = propertyFor(NestedOne::x)
        val nestedTwoY = propertyFor(NestedTwo::y)

        listOf(
            ModelMutationRequest(
                ScopeLocation.fromTopLevel().inObjectsOfType(typeFor<NestedOne>()),
                ModelMutation.SetPropertyValue(nestedOneX, NewValueNodeProvider.ArgumentBased { valueFromString(it[xParam].toString())!! }, Ignore)
            ),
            ModelMutationRequest(
                ScopeLocation.fromTopLevel().inObjectsOfType(typeFor<NestedOne>()).inObjectsConfiguredBy(functionFor(NestedOne::nestedTwo)),
                ModelMutation.SetPropertyValue(nestedTwoY, NewValueNodeProvider.ArgumentBased { valueFromString(it[yParam].toString())!! }, Ignore)
            )
        )
    }
}


private
val schema = object : EvaluationSchema {
    override val analysisSchema = schemaFromTypes(TopLevelReceiverForMutations::class, listOf(TopLevelReceiverForMutations::class, NestedOne::class, NestedTwo::class))
    override val operationGenerationId = DefaultOperationGenerationId.finalEvaluation
    override val analysisStatementFilter = analyzeEverything
}


internal
interface TopLevelReceiverForMutations {
    @Configuring
    fun nestedOne(configure: NestedOne.() -> Unit)
}


internal
interface NestedOne {
    @get:Restricted
    var x: Int

    @Configuring
    fun nestedTwo(configure: NestedTwo.() -> Unit)
}


internal
interface NestedTwo {
    @get:Restricted
    var y: Int
}
