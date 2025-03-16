/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.intrinsics.IntrinsicRuntimeFunctionCandidatesProvider
import org.gradle.internal.declarativedsl.intrinsics.gradleRuntimeIntrinsicsKClass
import org.gradle.internal.declarativedsl.schemaBuilder.FixedTopLevelFunctionDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.kotlinFunctionAsConfigureLambda
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.Assert
import org.junit.Test
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.kotlinFunction

class TopLevelFunctionInvocationTest {
    @Test
    fun `can invoke the Kotlin stdlib listOf function via intrinsics`() {
        val resolution = schema.resolve("""myStrings = listOf("one", "two")""")
        val result = evaluateRuntimeInstance(resolution)

        Assert.assertEquals(listOf("one", "two"), result.myStrings)
    }

    @Test
    fun `can invoke an ordinary auto-imported top-level function by simple name`() {
        val resolution = schema.resolve("""myStrings = testStrings(123)""")
        val result = evaluateRuntimeInstance(resolution)

        Assert.assertEquals(listOf("foo-123", "bar-123"), result.myStrings)
    }

    @Test
    fun `can invoke an ordinary auto-imported top-level function by fqn`() {
        val resolution = schema.resolve("""myStrings = ${javaClass.`package`.name}.testStrings(123)""")
        val result = evaluateRuntimeInstance(resolution)

        Assert.assertEquals(listOf("foo-123", "bar-123"), result.myStrings)
    }

    @Test
    fun `can invoke an ordinary top-level function by fqn`() {
        val resolution = schema.resolve("""myStrings = ${javaClass.`package`.name}.${::testStringsByFqn.name}()""")
        val result = evaluateRuntimeInstance(resolution)

        Assert.assertEquals(listOf("one", "two", "three"), result.myStrings)
    }

    private fun evaluateRuntimeInstance(resolution: ResolutionResult): TopLevel = runtimeInstanceFromResult(
        schema, resolution, kotlinFunctionAsConfigureLambda, RuntimeCustomAccessors.none, { TopLevel() }, runtimeFunctionResolver =
            CompositeFunctionResolver(
                listOf(
                    DefaultRuntimeFunctionResolver(kotlinFunctionAsConfigureLambda, IntrinsicRuntimeFunctionCandidatesProvider(listOf(gradleRuntimeIntrinsicsKClass))),
                    DefaultRuntimeFunctionResolver(kotlinFunctionAsConfigureLambda, DefaultRuntimeFunctionCandidatesProvider)
                )
            )
    )

    val schema = schemaFromTypes(
        TopLevel::class,
        listOf(TopLevel::class, List::class),
        externalFunctionDiscovery = FixedTopLevelFunctionDiscovery(
            listOf<KFunction<*>>(
                Class.forName("kotlin.collections.CollectionsKt").methods.single { it.name == "listOf" && it.parameters.singleOrNull()?.isVarArgs == true }.kotlinFunction!!,
                ::testStrings,
                ::testStringsByFqn
            )
        ),
        defaultImports = listOf(
            DefaultFqName.parse("kotlin.collections.listOf"),
            DefaultFqName.parse(javaClass.`package`.name + "." + ::testStrings.name)
        )
    )

    class TopLevel {
        @Suppress("unused")
        @get:Restricted
        var myStrings: List<String> = emptyList()
    }
}

@Restricted
internal fun testStrings(x: Int): List<String> = listOf("foo-$x", "bar-$x")

@Restricted
internal fun testStringsByFqn(): List<String> = listOf("one", "two", "three")
