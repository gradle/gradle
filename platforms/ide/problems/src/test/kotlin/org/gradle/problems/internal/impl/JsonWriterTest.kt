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
    fun `can produce valid multilevel json`() {
        assertThat(
            jsonModelFor {
                jsonObject {
                    property("solutions") {
                        jsonList(listOf("solo")) { solution ->
                            writeStructuredMessage(StructuredMessage.Builder().text(solution).build())
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

    @Test
    fun `no entries in list`() {
        assertThat(
            jsonModelFor {
                jsonObject {
                    property("solutions") {
                        jsonList(listOf<String>()) { solution ->
                            writeStructuredMessage(StructuredMessage.Builder().text(solution).build())
                        }
                    }
                }
            },
            hasEntry("solutions", listOf<String>())
        )
    }

    @Test
    fun `single entry in list`() {
        assertThat(
            jsonModelFor {
                jsonObject {
                    property("solutions") {
                        jsonList(listOf("one")) { solution ->
                            writeStructuredMessage(StructuredMessage.Builder().text(solution).build())
                        }
                    }
                }
            },
            hasEntry(
                "solutions",
                listOf(
                    listOf(
                        mapOf("text" to "one")
                    )
                )
            )
        )
    }

    @Test
    fun `multiple entries in list`() {
        assertThat(
            jsonModelFor {
                jsonObject {
                    property("solutions") {
                        jsonList(listOf("first", "second")) { solution ->
                            writeStructuredMessage(StructuredMessage.Builder().text(solution).build())
                        }
                    }
                }
            },
            hasEntry(
                "solutions",
                listOf(
                    listOf(
                        mapOf("text" to "first")
                    ),
                    listOf(
                        mapOf("text" to "second")
                    )
                )
            )
        )
    }

    @Test
    fun `no entries in object list`() {
        assertThat(
            jsonModelFor {
                jsonObject {
                    property("solutions") {
                        jsonObjectList(listOf<String>()) { solution ->
                            writeStructuredMessage(StructuredMessage.Builder().text(solution).build())
                        }
                    }
                }
            },
            hasEntry("solutions", listOf<String>())
        )
    }

    @Test
    fun `single entry in object list`() {
        assertThat(
            jsonModelFor {
                jsonObject {
                    property("solutions") {
                        jsonObjectList(listOf("solo")) { solution ->
                            property("text", solution)
                        }
                    }
                }
            },
            hasEntry(
                "solutions",
                listOf(
                    mapOf("text" to "solo")
                )
            )
        )
    }

    @Test
    fun `multiple entries in object list`() {
        assertThat(
            jsonModelFor {
                jsonObject {
                    property("solutions") {
                        jsonObjectList(listOf("first", "second")) { solution ->
                            property("text", solution)
                        }
                    }
                }
            },
            hasEntry(
                "solutions",
                listOf(
                    mapOf("text" to "first"),
                    mapOf("text" to "second")
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
