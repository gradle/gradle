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

package org.gradle.problems.internal.impl

import groovy.json.JsonSlurper
import org.gradle.internal.cc.impl.problems.JsonWriter
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.configuration.problems.writeStructuredMessage
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasEntry
import org.junit.Test
import java.io.StringWriter

class JsonWriterTest {

    @Test
    fun `writer produces valid multilevel JSON`() {
        assertThat(
            jsonModelFor {
                jsonObject {
                    property("solutions") {
                        jsonList(listOf("solo").iterator()) { solution ->
                            writeStructuredMessage(
                                StructuredMessage.Builder()
                                    .text(solution).build()
                            )
                        }
                    }
                }
            },
            hasEntry(
                "solutions",
                listOf(
                    listOf(
                        mapOf("text" to "solo")
                    )
                )
            )
        )
    }

    private
    fun jsonModelFor(builder: JsonWriter.() -> Unit): Map<String, Any> {
        return JsonSlurper().parseText(
            StringWriter().also {
                JsonWriter(it).apply(builder)
            }.toString()
        ).uncheckedCast()
    }

}
