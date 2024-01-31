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

package com.example.com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm

import com.h0tk3y.kotlin.staticObjectNotation.Configuring
import com.h0tk3y.kotlin.staticObjectNotation.Restricted
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ConfigureAccessor
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataConstructor
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataMemberFunction
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataTopLevelFunction
import com.h0tk3y.kotlin.staticObjectNotation.analysis.FunctionSemantics
import com.h0tk3y.kotlin.staticObjectNotation.analysis.FunctionSemantics.AccessAndConfigure.ReturnType.UNIT
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ResolutionResult
import com.h0tk3y.kotlin.staticObjectNotation.analysis.SchemaMemberFunction
import com.h0tk3y.kotlin.staticObjectNotation.analysis.SchemaTypeRefContext
import com.h0tk3y.kotlin.staticObjectNotation.demo.assignmentTrace
import com.h0tk3y.kotlin.staticObjectNotation.demo.resolve
import com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm.MemberFunctionResolver
import com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm.ReflectionRuntimePropertyResolver
import com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm.RestrictedReflectionToObjectConverter
import com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm.RuntimeCustomAccessors
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.ReflectionContext
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.reflect
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.DataSchemaBuilder
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.DefaultFunctionExtractor
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.FunctionExtractor
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.isPublicAndRestricted
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.kotlinFunctionAsConfigureLambda
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.plus
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.schemaFromTypes
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.toDataTypeRef
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.treatInterfaceAsConfigureLambda
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
