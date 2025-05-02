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

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.schemaBuilder.kotlinFunctionAsConfigureLambda
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.Test


class EmptyBlocksTest {
    @Test
    fun `empty configuring block leads to object access`() {
        val resolution = schema.resolve(
            """
            configuring { }
            """.trimIndent()
        )

        val result = runtimeInstanceFromResult(schema, resolution, kotlinFunctionAsConfigureLambda, RuntimeCustomAccessors.none, ::TopLevel)

        assertTrue { result.configuredLazy.isInitialized() }
    }

    @Test
    fun `configuring function with no block for Kotlin default param leads to object access`() {
        val resolution = schema.resolve(
            """
            configuring()
            """.trimIndent()
        )

        val result = runtimeInstanceFromResult(schema, resolution, kotlinFunctionAsConfigureLambda, RuntimeCustomAccessors.none, ::TopLevel)

        assertTrue { result.configuredLazy.isInitialized() }
    }

    @Test
    fun `empty adding block ensures that the object is created`() {
        val resolution = schema.resolve(
            """
            adding()
            adding { }
            adding { x = 2 }
            addingWithArg(3)
            addingWithArg(4) { }
            """.trimIndent()
        )

        val result = runtimeInstanceFromResult(schema, resolution, kotlinFunctionAsConfigureLambda, RuntimeCustomAccessors.none, ::TopLevel)

        assertEquals(listOf(0, 0, 2, 3, 4), result.added.map { it.x })
    }


    private
    val schema = schemaFromTypes(TopLevel::class, this::class.nestedClasses)


    class TopLevel {
        val configuredLazy = lazy { Inner() }
        val added = mutableListOf<Inner>()

        @Suppress("unused")
        @Configuring
        fun configuring(block: Inner.() -> Unit = { }) {
            configuredLazy.value.block()
        }

        @Adding
        fun adding(block: Inner.() -> Unit = { }) =
            Inner().also(block).also(added::add)

        @Suppress("unused")
        @Adding
        fun addingWithArg(x: Int = 100, block: Inner.() -> Unit = { }) =
            Inner().also(block).also(added::add).also { it.x = x }
    }

    class Inner {
        @get:Restricted
        var x: Int = 0
    }
}
