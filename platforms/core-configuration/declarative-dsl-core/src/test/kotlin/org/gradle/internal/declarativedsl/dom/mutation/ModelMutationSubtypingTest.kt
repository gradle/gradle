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

import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.internal.declarativedsl.dom.resolution.documentWithResolution
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.gradle.internal.declarativedsl.schemaUtils.functionFor
import org.gradle.internal.declarativedsl.schemaUtils.propertyFor
import org.gradle.internal.declarativedsl.schemaUtils.typeFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.Test


class ModelMutationSubtypingTest {
    private
    val planner = DefaultModelToDocumentMutationPlanner()

    @Test
    fun `can match subtype when supertype is specified`() {
        val code = """
            configureSub {
                x = 0
            }
        """.trimIndent()

        val doc = documentWithResolution(schema, ParseTestUtil.parse(code))

        val mutation = ModelMutationRequest(
            ScopeLocation.fromTopLevel().inObjectsOfType(schema.typeFor<NestedSuper>()),
            ModelMutation.SetPropertyValue(schema.propertyFor(NestedSuper::x), NewValueNodeProvider.Constant(valueFromString("1")!!))
        )

        val plan = planner.planModelMutation(schema, doc, mutation, mutationArguments { })
        assertEquals(
            doc.document.elementNamed("configureSub").propertyNamed("x").value,
            (plan.documentMutations.single() as DocumentMutation.ValueTargetedMutation.ReplaceValue).targetValue
        )
    }

    @Test
    fun `can match an overriding function when the supertype's function is specified`() {
        val code = """
            configureSub {
                nestedNotInHierarchy {
                    x = 0
                }
            }
        """.trimIndent()

        val doc = documentWithResolution(schema, ParseTestUtil.parse(code))

        val mutation = ModelMutationRequest(
            ScopeLocation.fromTopLevel().inObjectsOfType(schema.typeFor<NestedSub>()).inObjectsConfiguredBy(schema.functionFor(NestedSuper::nestedNotInHierarchy)),
            ModelMutation.SetPropertyValue(schema.propertyFor(NotInHierarchy::x), NewValueNodeProvider.Constant(valueFromString("1")!!))
        )

        val plan = planner.planModelMutation(schema, doc, mutation, mutationArguments { })
        assertEquals(
            doc.document.elementNamed("configureSub").elementNamed("nestedNotInHierarchy").propertyNamed("x").value,
            (plan.documentMutations.single() as DocumentMutation.ValueTargetedMutation.ReplaceValue).targetValue
        )
    }

    @Test
    fun `does not match the supertype's function when the overriding function is specified`() {
        val code = """
            configureSuper {
                nestedNotInHierarchy {
                    x = 0
                }
            }
        """.trimIndent()

        val doc = documentWithResolution(schema, ParseTestUtil.parse(code))

        val mutation = ModelMutationRequest(
            ScopeLocation.inAnyScope().inObjectsConfiguredBy(schema.functionFor(NestedSub::nestedNotInHierarchy)),
            ModelMutation.SetPropertyValue(schema.propertyFor(NotInHierarchy::x), NewValueNodeProvider.Constant(valueFromString("1")!!))
        )

        val plan = planner.planModelMutation(schema, doc, mutation, mutationArguments { })
        assertTrue { plan.documentMutations.isEmpty() }
    }

    @Test
    fun `does not match an unrelated type's property with the same name`() {
        val code = """
            configureSuper {
                nestedNotInHierarchy {
                    x = 0
                }
            }
        """.trimIndent()

        val doc = documentWithResolution(schema, ParseTestUtil.parse(code))

        val mutation = ModelMutationRequest(
            ScopeLocation.inAnyScope().inObjectsConfiguredBy(schema.functionFor(NestedSub::nestedNotInHierarchy)),
            ModelMutation.SetPropertyValue(
                schema.propertyFor(NestedSuper::x), // <- this is a property of NestedSuper, while the scope points to NotInHierarchy
                NewValueNodeProvider.Constant(valueFromString("1")!!)
            )
        )

        val plan = planner.planModelMutation(schema, doc, mutation, mutationArguments { })
        assertTrue { plan.documentMutations.isEmpty() }
    }
}


private
val schema = schemaFromTypes(SchemaWithSubtypes::class, listOf(SchemaWithSubtypes::class, NestedSuper::class, NestedSub::class, NotInHierarchy::class))


internal
interface SchemaWithSubtypes {
    @Configuring
    fun configureSuper(configure: NestedSuper.() -> Unit)

    @Configuring
    fun configureSub(configure: NestedSub.() -> Unit)

    @Configuring
    fun configureNotInHierarchy(configure: NotInHierarchy.() -> Unit)
}


internal
interface NestedSuper {
    @get:Restricted
    var x: Int

    @Configuring
    fun nestedNotInHierarchy(configure: NotInHierarchy.() -> Unit)
}


internal
interface NestedSub : NestedSuper {
    @Configuring
    override fun nestedNotInHierarchy(configure: NotInHierarchy.() -> Unit)
}


internal
interface NotInHierarchy {
    @get:Restricted
    var x: Int
}
