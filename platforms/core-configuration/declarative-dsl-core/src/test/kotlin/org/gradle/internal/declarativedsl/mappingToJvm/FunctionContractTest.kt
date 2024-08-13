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
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.schemaBuilder.kotlinFunctionAsConfigureLambda
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.Test


class FunctionContractTest {
    @Test
    fun `should invoke a configuring function only once`() {
        val resolution = schema.resolve(
            """
            configure { }
            configure { x = 1 }
            configure { y = 2 }
            """.trimIndent()
        )

        val result = runtimeInstanceFromResult(schema, resolution, kotlinFunctionAsConfigureLambda, RuntimeCustomAccessors.none, ::Receiver)

        assertEquals(1, result.x)
        assertEquals(2, result.y)
        assertEquals(1, result.invokedTimes)
    }

    private
    val schema = schemaFromTypes(Receiver::class, this::class.nestedClasses)

    class Receiver {

        var invokedTimes = 0

        @get:Restricted
        var x: Int = 0

        @get:Restricted
        var y: Int = 0
        @Configuring
        fun configure(configure: Receiver.() -> Unit) {
            configure(this)
            invokedTimes++
        }
    }
}
