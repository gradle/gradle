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

import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.schemaBuilder.kotlinFunctionAsConfigureLambda
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.Assert
import org.junit.Test

class ConfiguringFunctionWithIdentityTest {
    @Test
    fun `maps objects with different identity keys to different JVM objects, same keys to same objects`() {
        val resolution = schema.resolve(
            """
            itemByName("one") {
                x = 1
            }
            itemByName("one") {
                y = 1
            }
            itemByName("two") {
                x = 2
            }
            itemByName("two") {
                y = 2
            }
            """.trimIndent()
        )
        Assert.assertEquals(setOf(Item("one", 1, 1), Item("two", 2, 2)), objectFrom(resolution).items.toSet())
    }

    private fun objectFrom(resolution: ResolutionResult) =
        runtimeInstanceFromResult(schema, resolution, kotlinFunctionAsConfigureLambda, RuntimeCustomAccessors.none, ::TopLevel)

    val schema = schemaFromTypes(
        TopLevel::class,
        this::class.nestedClasses
    )

    class TopLevel {
        val items: MutableList<Item> = mutableListOf()

        @Configuring
        @Suppress("unused")
        fun itemByName(name: String, configure: Item.() -> Unit) {
            Item(name).also(items::add).also(configure)
        }
    }

    data class Item(val name: String, @get:Restricted var x: Int = 0, @get:Restricted var y: Int = 0)
}
