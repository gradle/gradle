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

package org.gradle.internal.declarativedsl.common

import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.ErrorReason
import org.gradle.internal.declarativedsl.analysis.analyzeEverything
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.evaluationSchema.AnalysisSchemaComponent
import org.gradle.internal.declarativedsl.evaluationSchema.buildEvaluationAndConversionSchema
import org.gradle.internal.declarativedsl.evaluationSchema.interpretWithConversion
import org.gradle.internal.declarativedsl.schemaBuilder.DefaultImportsProvider
import org.gradle.internal.declarativedsl.schemaBuilder.TopLevelFunctionDiscovery
import org.junit.Assert
import org.junit.Test
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

class StandardLibraryComponentTest {
    @Test
    fun `can resolve and invoke mapOf with pairs produced by the 'to' infix function`() {
        val target = TopLevel()
        schema.interpretWithConversion(target, """myStrIntMap = mapOf("one" to 1, "two" to 2)""")
        Assert.assertEquals(mapOf("one" to 1, "two" to 2), target.myStrIntMap)
    }

    @Test
    fun `can augment a map-typed property`() {
        val target = TopLevel().also { it.myStrIntMap = mapOf("one" to 1) }

        schema.interpretWithConversion(target, """myStrIntMap += mapOf("two" to 2, "three" to 3)""")
        Assert.assertEquals(mapOf("one" to 1, "two" to 2, "three" to 3), target.myStrIntMap)
    }

    @Test
    fun `map assignment and augmentation are covariant on value type`() {
        val target = TopLevel()
        schema.interpretWithConversion(target, """myIntSuperMap = mapOf(1 to sup(), 2 to sub())""")
        schema.interpretWithConversion(target, """myIntSuperMap += mapOf(3 to sup(), 4 to sub())""")
        schema.interpretWithConversion(target, """myIntSuperMap += minusOneToSub()""")

        Assert.assertEquals(mapOf(1 to target.sup(), 2 to target.sub(), 3 to target.sup(), 4 to target.sub(), -1 to target.sub()), target.myIntSuperMap)
    }

    @Test
    fun `fails to resolve a mapOf factory with an incorrect key type`() {
        val code = """myStrIntMap = mapOf(1 to 1)"""
        val resolution = schema.analysisSchema.resolve(code)
        Assert.assertTrue(resolution.errors.any {
            it.errorReason is ErrorReason.UnresolvedFunctionCallSignature &&
                it.element.sourceData.text() == """mapOf(1 to 1)"""
        })
    }

    @Test
    fun `fails to resolve a mapOf factory with an incorrect value type`() {
        val code = """myStrIntMap = mapOf("one" to true)"""
        val resolution = schema.analysisSchema.resolve(code)
        Assert.assertTrue(resolution.errors.any {
            it.errorReason is ErrorReason.UnresolvedFunctionCallSignature &&
                it.element.sourceData.text() == """mapOf("one" to true)"""
        })
    }

    @Test
    fun `cannot use opaque value factories in map keys passed to the 'to' function`() {
        val code = """myStrIntMap = mapOf("one" to 1, opaqueString() to 2)"""
        val resolution = schema.analysisSchema.resolve(code)
        Assert.assertTrue(resolution.errors.any {
            it.element.sourceData.text() == "opaqueString() to 2" &&
                (it.errorReason as? ErrorReason.OpaqueArgumentForIdentityParameter)
                    ?.argument?.originElement?.sourceData?.text() == "opaqueString()"
        })
    }

    val schema = buildEvaluationAndConversionSchema(TopLevel::class, analyzeEverything) {
        gradleDslGeneralSchema()

        /**
         * The DCL schema building facilities limit the [Map] value construction to [mapOf], but we also want to test that the rest of the pipeline can
         * handle not just [mapOf] literals but other ways to create a map. For that, import [minusOneToSub] into the schema not by annotating it as
         * a member but in a similar way to how [mapOf] itself is registered in the schema: as a top-level function (which plugin authors cannot contribute).
         */
        registerAnalysisSchemaComponent(object : AnalysisSchemaComponent {
            override fun topLevelFunctionDiscovery(): List<TopLevelFunctionDiscovery> = listOf(object : TopLevelFunctionDiscovery {
                override fun discoverTopLevelFunctions(): List<KFunction<*>> = listOf(::minusOneToSub)
            })

            override fun defaultImportsProvider(): List<DefaultImportsProvider> = listOf(object : DefaultImportsProvider {
                override fun defaultImports(): List<FqName> = listOf(DefaultFqName.parse(::minusOneToSub.run { "${javaMethod!!.declaringClass.`package`.name}.$name" }))
            })
        })
    }
}

internal class TopLevel {
    @get:Restricted
    var myStrIntMap: Map<String, Int> = mapOf()

    @get:Restricted
    var myIntSuperMap: Map<Int, Super> = mapOf()

    @Restricted
    fun opaqueString() = String()

    @Restricted
    fun sup() = Super()

    @Restricted
    fun sub() = Sub()
}

open class Super {
    override fun equals(other: Any?): Boolean = other != null && other::class == Super::class
    override fun hashCode(): Int = this::class.hashCode()
}

class Sub : Super() {
    override fun equals(other: Any?): Boolean = other != null && other::class == Sub::class
    override fun hashCode(): Int = this::class.hashCode()
}

@Suppress("unused")
fun minusOneToSub() = mapOf(-1 to Sub())
