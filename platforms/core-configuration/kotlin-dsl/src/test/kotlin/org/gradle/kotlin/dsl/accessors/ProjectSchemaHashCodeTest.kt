/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.accessors

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskContainer

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class ProjectSchemaHashCodeTest : TestWithClassPath() {

    @Test
    fun `hash code takes configurations into account`() {

        assertThat(
            hashCodeFor(
                configurations = listOf("api")
            ),
            equalTo(
                hashCodeFor(
                    configurations = listOf("api")
                )
            )
        )

        assertThat(
            hashCodeFor(
                configurations = listOf("api")
            ),
            not(
                equalTo(
                    hashCodeFor(
                        configurations = listOf("implementation")
                    )
                )
            )
        )
    }

    @Test
    fun `hash code is configuration order insensitive`() {

        assertThat(
            hashCodeFor(
                configurations = listOf("api", "implementation")
            ),
            equalTo(
                hashCodeFor(
                    configurations = listOf("implementation", "api")
                )
            )
        )
    }

    @Test
    fun `hash code takes container element names into account`() {

        assertThat(
            hashCodeFor(
                containerElements = listOf(
                    entry<TaskContainer, DefaultTask>("assemble")
                )
            ),
            equalTo(
                hashCodeFor(
                    containerElements = listOf(
                        entry<TaskContainer, DefaultTask>("assemble")
                    )
                )
            )
        )

        assertThat(
            hashCodeFor(
                containerElements = listOf(
                    entry<TaskContainer, DefaultTask>("assemble")
                )
            ),
            not(
                equalTo(
                    hashCodeFor(
                        containerElements = listOf(
                            entry<TaskContainer, DefaultTask>("clean")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `hash code takes container element types into account`() {

        assertThat(
            hashCodeFor(
                containerElements = listOf(
                    entry<TaskContainer, Delete>("clean")
                )
            ),
            equalTo(
                hashCodeFor(
                    containerElements = listOf(
                        entry<TaskContainer, Delete>("clean")
                    )
                )
            )
        )

        assertThat(
            hashCodeFor(
                containerElements = listOf(
                    entry<TaskContainer, Delete>("clean")
                )
            ),
            not(
                equalTo(
                    hashCodeFor(
                        containerElements = listOf(
                            entry<TaskContainer, DefaultTask>("clean")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `hash code takes task names into account`() {

        assertThat(
            hashCodeFor(
                tasks = listOf(
                    entry<TaskContainer, DefaultTask>("assemble")
                )
            ),
            equalTo(
                hashCodeFor(
                    tasks = listOf(
                        entry<TaskContainer, DefaultTask>("assemble")
                    )
                )
            )
        )

        assertThat(
            hashCodeFor(
                tasks = listOf(
                    entry<TaskContainer, DefaultTask>("assemble")
                )
            ),
            not(
                equalTo(
                    hashCodeFor(
                        tasks = listOf(
                            entry<TaskContainer, DefaultTask>("clean")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `hash code takes task types into account`() {

        assertThat(
            hashCodeFor(
                tasks = listOf(
                    entry<TaskContainer, Delete>("clean")
                )
            ),
            equalTo(
                hashCodeFor(
                    tasks = listOf(
                        entry<TaskContainer, Delete>("clean")
                    )
                )
            )
        )

        assertThat(
            hashCodeFor(
                tasks = listOf(
                    entry<TaskContainer, Delete>("clean")
                )
            ),
            not(
                equalTo(
                    hashCodeFor(
                        tasks = listOf(
                            entry<TaskContainer, DefaultTask>("clean")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `hash code distinguishes between extensions, conventions and container elements`() {

        val entries = listOf(
            entry<TaskContainer, String>("tag")
        )

        assertThat(
            hashCodeFor(
                extensions = entries
            ),
            not(
                equalTo(
                    hashCodeFor(
                        conventions = entries
                    )
                )
            )
        )

        assertThat(
            hashCodeFor(
                extensions = entries
            ),
            not(
                equalTo(
                    hashCodeFor(
                        containerElements = entries
                    )
                )
            )
        )

        assertThat(
            hashCodeFor(
                conventions = entries
            ),
            not(
                equalTo(
                    hashCodeFor(
                        containerElements = entries
                    )
                )
            )
        )
    }

    private
    fun hashCodeFor(
        extensions: TypedProjectSchemaEntryList = emptyList(),
        conventions: TypedProjectSchemaEntryList = emptyList(),
        tasks: TypedProjectSchemaEntryList = emptyList(),
        containerElements: TypedProjectSchemaEntryList = emptyList(),
        configurations: List<String> = emptyList()
    ) = hashCodeFor(
        projectSchemaWith(
            extensions = extensions,
            conventions = conventions,
            tasks = tasks,
            containerElements = containerElements,
            configurations = configurations
        )
    )
}
