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

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.dom.mutation.ModelMutation.AddConfiguringBlockIfAbsent
import org.gradle.internal.declarativedsl.dom.resolution.documentWithResolution
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.gradle.internal.declarativedsl.schemaUtils.functionFor
import org.gradle.internal.declarativedsl.schemaUtils.propertyFor
import org.gradle.internal.declarativedsl.schemaUtils.typeFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.Test
import org.junit.jupiter.api.Assertions.fail


internal
class MutationApplicabilityCheckerTest {

    @Test
    fun `detects applicability of element addition`() {
        val doc = documentWithResolution(
            schema, ParseTestUtil.parse(
                """
                nested {
                    x = 1
                    f {
                        x = 1
                        f {
                        }
                    }
                }
                """.trimIndent()
            )
        )

        val result = MutationApplicabilityChecker(schema, doc).checkApplicability(addF)

        assertEquals(
            setOf(
                MutationApplicability.ScopeWithoutAffectedNodes(doc.document.elementNamed("nested")),
                MutationApplicability.ScopeWithoutAffectedNodes(doc.document.elementNamed("nested").elementNamed("f")),
                MutationApplicability.ScopeWithoutAffectedNodes(doc.document.elementNamed("nested").elementNamed("f").elementNamed("f")),
            ), result.toSet()
        )
    }

    @Test
    fun `detects applicability of a set property mutation`() {
        val doc = documentWithResolution(
            schema, ParseTestUtil.parse(
                """
                    nested {
                        x = 1
                        f {
                            x = 1
                            f {
                            }
                        }
                    }
                """.trimIndent()
            )
        )

        val result = MutationApplicabilityChecker(schema, doc).checkApplicability(setX)

        assertEquals(
            setOf(
                MutationApplicability.AffectedNode(doc.document.elementNamed("nested").propertyNamed("x")),
                MutationApplicability.AffectedNode(doc.document.elementNamed("nested").elementNamed("f").propertyNamed("x")),
                MutationApplicability.ScopeWithoutAffectedNodes(doc.document.elementNamed("nested").elementNamed("f").elementNamed("f")),
            ), result.toSet()
        )
    }

    @Test
    fun `detects applicability of a mutation that produces a block and adds content to it -- when the block does not exist`() {
        val doc = documentWithResolution(
            schema, ParseTestUtil.parse(
                """
                nested {
                }
                """.trimIndent()
            )
        )

        val result = MutationApplicabilityChecker(schema, doc).checkApplicability(addCIfAbsentThenAddF)

        assertEquals(
            setOf(
                MutationApplicability.ScopeWithoutAffectedNodes(doc.document.elementNamed("nested")),
            ), result.toSet()
        )
    }

    @Test
    fun `detects applicability of a mutation that produces a block and adds content to it -- when the block exists`() {
        val doc = documentWithResolution(
            schema, ParseTestUtil.parse(
                """
                nested {
                    c { }
                }
                """.trimIndent()
            )
        )

        val result = MutationApplicabilityChecker(schema, doc).checkApplicability(addCIfAbsentThenAddF)

        assertEquals(
            setOf(
                MutationApplicability.ScopeWithoutAffectedNodes(doc.document.elementNamed("nested").elementNamed("c")),
            ), result.toSet()
        )
    }

    @Test
    fun `does not fail if the document has errors`() {
        val doc = documentWithResolution(
            schema, ParseTestUtil.parse(
                """
                nested {
                    unresolved { }
                    x = "foo" // won't type-check

                    ^_^ syntax error!

                    f {
                        x = 1
                    }

                    @UnsupportedFeatureAnnotation
                    something = "something"
                }
                """.trimIndent()
            )
        )

        val addFResult = MutationApplicabilityChecker(schema, doc).checkApplicability(addF)

        assertEquals(
            setOf(
                MutationApplicability.ScopeWithoutAffectedNodes(doc.document.elementNamed("nested")),
                MutationApplicability.ScopeWithoutAffectedNodes(doc.document.elementNamed("nested").elementNamed("unresolved")),
                MutationApplicability.ScopeWithoutAffectedNodes(doc.document.elementNamed("nested").elementNamed("f")),
            ),
            addFResult.toSet()
        )

        val setXResult = MutationApplicabilityChecker(schema, doc).checkApplicability(setX)

        assertEquals(
            setOf(
                MutationApplicability.AffectedNode(doc.document.elementNamed("nested").elementNamed("f").propertyNamed("x")),
                // For now, the planner will think that 'x = "foo"' does not match the property because of type mismatch:
                MutationApplicability.ScopeWithoutAffectedNodes(doc.document.elementNamed("nested")),
            ),
            setXResult.toSet()
        )
    }

    @Test
    fun `reports no applicability and does not fail on incompatible mutations`() {
        val incompatibleMutation = object : MutationDefinition {
            override val id: String = "com.example.incompatible"
            override val name: String = "Incompatible"
            override val description: String = "This mutation is not compatible with any schema and throws an exception on planning"
            override val parameters: List<MutationParameter<*>> = emptyList()

            override fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean = false

            override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> =
                fail("tried to define the mutation sequence for an incompatible mutation!")
        }

        assertEquals(emptyList<MutationApplicability>(), MutationApplicabilityChecker(schema, documentWithResolution(schema, ParseTestUtil.parse(""))).checkApplicability(incompatibleMutation))
    }

    private
    val schema = schemaFromTypes(TopLevel::class, listOf(TopLevel::class, Nested::class))
}


private
val setX = object : MutationDefinition {
    override val id: String = "com.example.setx"
    override val name: String = "set x"
    override val description: String = "sets x in all nested objects"

    override val parameters: List<MutationParameter<*>> = emptyList()

    override fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean = true

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> = listOf(
        ModelMutationRequest(
            ScopeLocation.inAnyScope(),
            ModelMutation.SetPropertyValue(projectAnalysisSchema.propertyFor(Nested::x), NewValueNodeProvider.Constant(valueFromString("2")!!))
        )
    )
}


private
val addF = object : MutationDefinition {
    override val id: String = "com.example.addf"
    override val name: String = "add f"
    override val description: String = "adds f in all nested objects"

    override val parameters: List<MutationParameter<*>> = emptyList()

    override fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean = true

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> = listOf(
        ModelMutationRequest(
            ScopeLocation.fromTopLevel().alsoInNestedScopes(),
            ModelMutation.AddNewElement(NewElementNodeProvider.Constant(elementFromString("f()")!!))
        )
    )
}


private
val addCIfAbsentThenAddF = object : MutationDefinition {
    override val id: String = "com.example.addAndSet"
    override val name: String = "add c { } and add f() in it"
    override val description: String = "add c { } block to nested { } if it is not there yet, in that block, add f()"

    override val parameters: List<MutationParameter<*>> = emptyList()

    override fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean = true

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> = listOf(
        ModelMutationRequest(
            ScopeLocation.fromTopLevel().inObjectsOfType(projectAnalysisSchema.typeFor<Nested>()),
            AddConfiguringBlockIfAbsent(projectAnalysisSchema.functionFor(Nested::c))
        ),
        ModelMutationRequest(
            ScopeLocation.fromTopLevel()
                .inObjectsConfiguredBy(projectAnalysisSchema.functionFor(TopLevel::nested))
                .alsoInNestedScopes()
                .inObjectsConfiguredBy(projectAnalysisSchema.functionFor(Nested::c)),
            ModelMutation.AddNewElement(NewElementNodeProvider.Constant(elementFromString("f()")!!))
        )
    )
}


internal
interface TopLevel {
    @Configuring
    fun nested(configure: Nested.() -> Unit)
}


internal
interface Nested {
    @get:Restricted
    var x: Int

    @Adding
    fun f(configure: Nested.() -> Unit): Nested

    @Configuring
    fun c(configure: Nested.() -> Unit): Nested
}
