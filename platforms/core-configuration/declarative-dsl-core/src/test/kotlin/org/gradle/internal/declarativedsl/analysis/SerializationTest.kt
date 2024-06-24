/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.demo.demoPlugins.schema
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.serialization.SchemaSerialization
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs


object SerializationTest {
    private
    val pluginsSchema: AnalysisSchema = schema

    @Test
    fun `schema is serializable`() {
        val serialized = SchemaSerialization.schemaToJsonString(pluginsSchema)
        val deserialized = SchemaSerialization.schemaFromJsonString(serialized)

        val result = deserialized.resolve(
            """
            plugins {
                id("test")
            }
            """.trimIndent()
        )

        assertEquals(1, result.additions.size)
        val idOrigin = result.additions.single().dataObject
        assertIs<ObjectOrigin.FunctionInvocationOrigin>(idOrigin)
        assertEquals("id", idOrigin.function.simpleName)
    }
}
