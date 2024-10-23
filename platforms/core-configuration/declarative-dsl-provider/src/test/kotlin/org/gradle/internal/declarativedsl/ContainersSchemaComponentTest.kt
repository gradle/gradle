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

package org.gradle.internal.declarativedsl

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.internal.AbstractNamedDomainObjectContainer
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.tasks.Internal
import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.model.annotations.ElementFactoryName
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.analysis.analyzeEverything
import org.gradle.internal.declarativedsl.common.gradleDslGeneralSchema
import org.gradle.internal.declarativedsl.schemaUtils.typeFor
import org.gradle.internal.declarativedsl.evaluationSchema.SimpleInterpretationSequenceStepWithConversion
import org.gradle.internal.declarativedsl.evaluationSchema.buildEvaluationAndConversionSchema
import org.gradle.internal.declarativedsl.evaluator.conversion.AnalysisAndConversionStepRunner
import org.gradle.internal.declarativedsl.evaluator.conversion.ConversionStepContext
import org.gradle.internal.declarativedsl.evaluator.runner.AnalysisStepContext
import org.gradle.internal.declarativedsl.evaluator.runner.AnalysisStepRunner
import org.gradle.internal.declarativedsl.schemaUtils.findType
import org.gradle.internal.declarativedsl.schemaUtils.hasFunctionNamed
import org.gradle.internal.declarativedsl.schemaUtils.singleFunctionNamed
import org.gradle.internal.reflect.Instantiator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainersSchemaComponentTest {
    @Test
    fun `imports container members into the schema`() {
        assertEquals(setOf("containerOne", "containerTwo"), schema.analysisSchema.typeFor<TopLevel>().memberFunctions.map { it.simpleName }.toSet())
        assertEquals(setOf("containerThree", "containerSubtype"), schema.analysisSchema.typeFor<Two>().memberFunctions.map { it.simpleName }.toSet())
    }

    @Test
    fun `uses element type names for factory function names`() {
        listOf("one", "two", "customFactoryName").forEach { name ->
            assertTrue(schema.analysisSchema.findType { type: DataClass -> type.hasFunctionNamed(name) } != null)
        }
    }

    @Test
    fun `ignores container members annotated as @Internal`() {
        assertFalse(schema.analysisSchema.typeFor<TopLevel>().hasFunctionNamed("internalContainer"))
    }

    @Test
    fun `discovers types used as container elements`() {
        assertTrue(schema.analysisSchema.dataClassTypesByFqName.keys.map { it.qualifiedName }.containsAll(listOf(One::class.qualifiedName, Two::class.qualifiedName, Three::class.qualifiedName)))
    }

    @Test
    fun `each container only has its own element factories`() {
        val containerOneFunctionSemantics = schema.analysisSchema.typeFor<TopLevel>().singleFunctionNamed("containerOne").function.semantics
        val configuredTypeRef = (containerOneFunctionSemantics as FunctionSemantics.AccessAndConfigure).configuredType
        val configuredType = SchemaTypeRefContext(schema.analysisSchema).resolveRef(configuredTypeRef) as DataClass
        assertTrue(configuredType.hasFunctionNamed("one"))
        assertFalse(configuredType.hasFunctionNamed("two"))
    }

    @Test
    fun `can create elements during object conversion`() {
        val receiver = TopLevel()

        AnalysisAndConversionStepRunner(AnalysisStepRunner())
            .runInterpretationSequenceStep(
                scriptIdentifier = "test",
                scriptSource = """
                    containerOne {
                        one("nameOne") {
                            x = 1
                        }
                    }

                    containerTwo {
                        two("nameTwo") {
                            y = 2
                            containerThree {
                                customFactoryName("nameThree") {
                                    z = 3
                                }
                                customFactoryName("nameThirty") {
                                    z = 30
                                }
                            }
                            containerSubtype {
                                customFactoryName("nameThreeHundred") {
                                    z = 300
                                }
                                configuringInSubtype { // check that the runtime function resolver distinguishes between the synthetic element factory and other functions
                                    z = 301
                                }
                                w = 4
                            }
                        }
                    }
                """.trimIndent(),
                SimpleInterpretationSequenceStepWithConversion("test", emptySet()) { schema },
                ConversionStepContext(receiver, AnalysisStepContext(emptyList(), emptyList()))
            )

        receiver.containerOne.single().run {
            assertEquals("nameOne", name)
            assertEquals(1, x)
        }
        receiver.getContainerTwo().single().run {
            assertEquals("nameTwo", name)
            assertEquals(2, y)

            assertEquals(
                setOf("nameThree" to 3, "nameThirty" to 30),
                containerThree.map { it.name to it.z }.toSet()
            )

            assertEquals(
                setOf("nameThreeHundred" to 300, "configuringInSubtype" to 301),
                containerSubtype.map { it.name to it.z }.toSet()
            )
            assertEquals(4, containerSubtype.w)
        }
    }

    private val schema = buildEvaluationAndConversionSchema(TopLevel::class, analyzeEverything) { gradleDslGeneralSchema() }

    class TopLevel {
        val containerOne: NamedDomainObjectContainer<One> = container(One::class.java)

        fun getContainerTwo(): NamedDomainObjectContainer<Two> = containerTwo

        private val containerTwo = container(Two::class.java)

        @Suppress("unused")
        @get:Internal
        val internalContainer: NamedDomainObjectContainer<One> = container(One::class.java)
    }

    class One(private val name: String) : Named {
        @get:Restricted
        var x: Int = 0

        override fun getName(): String = name
    }

    class Two(private val name: String) : Named {
        val containerThree: NamedDomainObjectContainer<Three> = container(Three::class.java)

        val containerSubtype: NdocSubtype = NdocSubtype(container(Three::class.java))

        @get:Restricted
        var y: Int = 0

        override fun getName(): String = name
    }

    @ElementFactoryName("customFactoryName")
    class Three(private val name: String) : Named {
        @get:Restricted
        var z: Int = 0

        override fun getName(): String = name
    }

    class NdocSubtype(container: NamedDomainObjectContainer<Three>) : NamedDomainObjectContainer<Three> by container {
        @get:Restricted
        var w: Int = 0

        @Suppress("unused")
        @Configuring
        fun configuringInSubtype(configure: Three.() -> Unit) {
            maybeCreate("configuringInSubtype").let(configure)
        }
    }
}

private fun <T : Any> container(type: Class<T>): NamedDomainObjectContainer<T> = object : AbstractNamedDomainObjectContainer<T>(
    type,
    mock<Instantiator> { mock ->
        on { mock.newInstance<Any>(any(), any()) }.then { invocation ->
            (invocation.getArgument(0) as Class<*>).constructors.single().newInstance(invocation.getArgument(1))
        }
    },
    CollectionCallbackActionDecorator.NOOP
) {
    override fun doCreate(name: String): T = instantiator.newInstance(type, name)
}
