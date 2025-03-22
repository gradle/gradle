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
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.schemaBuilder.kotlinFunctionAsConfigureLambda
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals

class VarargMappingToJvmTest {
    @Test
    fun `varargs with primitive type get properly converted`() {
        val code = """
            ints = myInts(1, 2, 3)
            moreInts = myGenericValues(1, 2, 3, 4)
            longs = myLongs(1L, 2L, 3L)
            booleans = myBooleans(true, false, true)
        """.trimIndent()

        val receiver = runtimeInstanceFromResult(schema, schema.resolve(code), kotlinFunctionAsConfigureLambda, RuntimeCustomAccessors.none, ::MyTypeWithVarargs)
        assertEquals(listOf(1, 2, 3), receiver.ints)
        assertEquals(listOf(1, 2, 3, 4), receiver.moreInts)
        assertEquals(listOf(1L, 2L, 3L), receiver.longs)
        assertEquals(listOf(true, false, true), receiver.booleans)
    }

    @Test
    fun `varargs with concrete types get properly converted`() {
        val code = """strings = myStrings("one", "two", "three")""".trimIndent()

        val receiver = runtimeInstanceFromResult(schema, schema.resolve(code), kotlinFunctionAsConfigureLambda, RuntimeCustomAccessors.none, ::MyTypeWithVarargs)
        assertEquals(listOf("one", "two", "three"), receiver.strings)
    }

    @Test
    fun `varargs with type argument get properly converted`() {
        val code = """strings = myListOf("one", "two", "three")""".trimIndent()

        val receiver = runtimeInstanceFromResult(schema, schema.resolve(code), kotlinFunctionAsConfigureLambda, RuntimeCustomAccessors.none, ::MyTypeWithVarargs)
        assertEquals(listOf("one", "two", "three"), receiver.strings)
    }

    val schema = schemaFromTypes(MyTypeWithVarargs::class, listOf(MyTypeWithVarargs::class, List::class))

    class MyTypeWithVarargs {
        @Suppress("unused")
        @Restricted
        fun <T> myListOf(vararg items: T): List<T> = items.toList()

        @Suppress("unused")
        @Restricted
        fun myStrings(vararg strings: String): List<String> = strings.toList()

        @Suppress("unused")
        @Restricted
        fun myInts(vararg ints: Int): List<Int> = ints.toList()

        @Suppress("unused")
        @Restricted
        fun myLongs(vararg longs: Long): List<Long> = longs.toList()

        @Suppress("unused")
        @Restricted
        fun myBooleans(vararg booleans: Boolean): List<Boolean> = booleans.toList()

        @Suppress("unused")
        @Restricted
        fun <T> myGenericValues(vararg values: T): List<T> = values.toList()

        @get:Restricted
        var strings: List<String> = emptyList()

        @get:Restricted
        var ints: List<Int> = emptyList()

        @get:Restricted
        var moreInts: List<Int> = emptyList()

        @get:Restricted
        var longs: List<Long> = emptyList()

        @get:Restricted
        var booleans: List<Boolean> = emptyList()
    }
}
