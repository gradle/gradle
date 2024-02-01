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

package org.gradle.internal.declarativedsl.mappingToJvm

import org.gradle.internal.declarativedsl.Configuring
import org.gradle.internal.declarativedsl.Restricted
import org.gradle.internal.declarativedsl.analysis.ConfigureAccessor
import org.gradle.internal.declarativedsl.analysis.DataConstructor
import org.gradle.internal.declarativedsl.analysis.DataMemberFunction
import org.gradle.internal.declarativedsl.analysis.DataTopLevelFunction
import org.gradle.internal.declarativedsl.analysis.FunctionSemantics
import org.gradle.internal.declarativedsl.analysis.FunctionSemantics.AccessAndConfigure.ReturnType.UNIT
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.analysis.SchemaMemberFunction
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.demo.assignmentTrace
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.objectGraph.ReflectionContext
import org.gradle.internal.declarativedsl.objectGraph.reflect
import org.gradle.internal.declarativedsl.schemaBuilder.DataSchemaBuilder
import org.gradle.internal.declarativedsl.schemaBuilder.DefaultFunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.isPublicAndRestricted
import org.gradle.internal.declarativedsl.schemaBuilder.kotlinFunctionAsConfigureLambda
import org.gradle.internal.declarativedsl.schemaBuilder.plus
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.gradle.internal.declarativedsl.schemaBuilder.toDataTypeRef
import org.gradle.internal.declarativedsl.schemaBuilder.treatInterfaceAsConfigureLambda
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.test.Test
import kotlin.test.assertEquals

object AccessorTest {
    @Test
    fun `uses custom accessor in mapping to JVM`() {
        val resolution = schema.resolve(
            """
            configureCustomInstance {
                x = 123
            }
        """.trimIndent()
        )
        assertEquals(123, runtimeInstanceFromResult(resolution).myHiddenInstance.x)
    }

    @Test
    fun `accesses receiver from runtime lambda argument mapping to JVM`() {
        val resolution = schema.resolve(
            """
            configureLambdaArgument {
                x = 123
            }
            configureLambdaArgumentWithCustomInterface {
                y = "test"
            }
        """.trimIndent()
        )
        val runtimeInstanceFromResult = runtimeInstanceFromResult(resolution)
        assertEquals(123, runtimeInstanceFromResult.myLambdaReceiver.x)
        assertEquals("test", runtimeInstanceFromResult.myLambdaReceiver.y)
    }

    private fun runtimeInstanceFromResult(resolution: ResolutionResult): MyReceiver {
        val trace = assignmentTrace(resolution)
        val context = ReflectionContext(SchemaTypeRefContext(schema), resolution, trace)
        val topLevel = reflect(resolution.topLevelReceiver, context)

        val runtimeInstance = MyReceiver()
        RestrictedReflectionToObjectConverter(
            emptyMap(),
            runtimeInstance,
            MemberFunctionResolver(configureLambdas),
            ReflectionRuntimePropertyResolver,
            runtimeCustomAccessors
        ).apply(topLevel)
        return runtimeInstance
    }

    private val runtimeCustomAccessors = object : RuntimeCustomAccessors {
        override fun getObjectFromCustomAccessor(receiverObject: Any, accessor: ConfigureAccessor.Custom): Any? =
            if (receiverObject is MyReceiver && accessor.customAccessorIdentifier == "test")
                receiverObject.myHiddenInstance
            else null
    }

    private val functionContributorWithCustomAccessor = object : FunctionExtractor {
        override fun memberFunctions(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> =
            if (kClass == MyReceiver::class) {
                listOf(
                    DataMemberFunction(
                        MyReceiver::class.toDataTypeRef(),
                        "configureCustomInstance",
                        emptyList(),
                        false,
                        FunctionSemantics.AccessAndConfigure(ConfigureAccessor.Custom(Configured::class.toDataTypeRef(), "test"), UNIT)
                    )
                )
            } else emptyList()

        override fun constructors(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<DataConstructor> = emptyList()
        override fun topLevelFunction(function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): DataTopLevelFunction? = null
    }

    val configureLambdas = kotlinFunctionAsConfigureLambda + treatInterfaceAsConfigureLambda(MyFunctionalInterface::class)

    val schema = schemaFromTypes(
        MyReceiver::class,
        this::class.nestedClasses,
        functionExtractor = DefaultFunctionExtractor(isPublicAndRestricted, configureLambdas) + functionContributorWithCustomAccessor
    )

    internal class MyReceiver {
        val myLambdaReceiver = Configured()

        @Configuring
        fun configureLambdaArgument(configure: Configured.() -> Unit) {
            configure(myLambdaReceiver)
        }

        @Configuring
        fun configureLambdaArgumentWithCustomInterface(configure: MyFunctionalInterface<Configured>) {
            configure.action(myLambdaReceiver)
        }

        val myHiddenInstance = Configured()
    }

    internal interface MyFunctionalInterface<in T> {
        fun action(t: T)
    }

    internal class Configured {
        @Restricted
        var x: Int = 0

        @Restricted
        var y: String = ""
    }
}
