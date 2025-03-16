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

import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.schemaBuilder.kotlinFunctionAsConfigureLambda
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.Test
import org.junit.jupiter.api.Assertions

class DefaultValueTest {

    @Test
    fun `enum property default value works`() {
        val resolution = schema.resolve(
            """
            e1 = e2
            """.trimIndent()
        )

        Assertions.assertEquals(Enum.B, runtimeInstanceFromResult(schema, resolution, kotlinFunctionAsConfigureLambda, RuntimeCustomAccessors.none, ::Receiver).e1)
    }

    private
    val schema = schemaFromTypes(Receiver::class, this::class.nestedClasses)

    class Receiver {

        @get:Restricted
        var e1: Enum = Enum.A

        @get:Restricted
        val e2: Enum = Enum.B
    }

    enum class Enum {
        A, B, C
    }

}
