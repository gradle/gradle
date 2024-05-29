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

package org.gradle.configurationcache.problems

import groovy.json.JsonSlurper
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.configuration.problems.DecoratedPropertyProblem
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessage
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasEntry
import org.junit.Test
import java.io.StringWriter


class JsonModelWriterTest {

    @Test
    fun `encodes model with empty strings correctly`() {
        assertThat(
            jsonModelFor {
                beginModel()
                writeDiagnostic(
                    DiagnosticKind.INPUT,
                    DecoratedPropertyProblem(
                        PropertyTrace.Unknown,
                        StructuredMessage.build { reference("") }
                    )
                )
                endModel("", "", "", 0)
            },
            hasEntry(
                "diagnostics",
                listOf(
                    mapOf(
                        "trace" to listOf(mapOf("kind" to "Unknown")),
                        "input" to listOf(mapOf("name" to ""))
                    )
                )
            )
        )
    }

    private
    fun jsonModelFor(builder: JsonModelWriter.() -> Unit): Map<String, Any> =
        JsonSlurper().parseText(
            StringWriter().also {
                JsonModelWriter(it).apply(builder)
            }.toString()
        ).uncheckedCast()
}
