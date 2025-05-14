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

package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.schemaBuilder.TopLevelFunctionDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.Assert
import org.junit.Test
import kotlin.reflect.KFunction

class InfixFunctionCallResolutionTest {

    @Test
    fun `resolves a correct infix 'to' function call`() {
        val result = schema.resolve("""myStringPair = "a" to "b"""")
        Assert.assertEquals("to", (result.assignments.single().rhs as ObjectOrigin.NewObjectFromTopLevelFunction).function.simpleName)
    }

    @Test
    fun `fails to resolve a call to the 'to' infix function written with the dot notation`() {
        val result = schema.resolve("""myStringPair = str().to("str")""")
        Assert.assertEquals(2, result.errors.size)
        Assert.assertTrue(result.errors.any { (it.errorReason as? ErrorReason.UnresolvedFunctionCallSignature)?.functionCall?.name == "to" })
    }

    @Suppress("unused")
    class TopLevel {
        @get:Restricted
        var myStringPair: MyStringPair = MyStringPair("", "")

        @Restricted
        fun str(): String = "s" + "tr"
    }

    val schema = schemaFromTypes(TopLevel::class, listOf(TopLevel::class, MyStringPair::class), object : TopLevelFunctionDiscovery {
        override fun discoverTopLevelFunctions(): List<KFunction<*>> = listOf(String::to)
    }, defaultImports = listOf(DefaultFqName.parse("${String::to.javaClass.`package`.name}.${String::to.name}")))
}


infix fun String.to(b: String) = MyStringPair(this, b)

data class MyStringPair(val a: String, val b: String)
