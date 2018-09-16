/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl

import org.gradle.api.Project
import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.eval
import org.gradle.kotlin.dsl.fixtures.newProjectBuilderProject

import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class NamedContainersEvalTest : TestWithTempFiles() {

    companion object {

        val preExistingConfigurations = listOf(
            "foo", "bar", "cabin", "castle"
        )

        val expectedConfigurationsExtendsFrom = listOf(
            "bar" to "foo",
            "cathedral" to "bazar",
            "castle" to "cabin",
            "hill" to "valley"
        )
    }

    private
    fun assertConfigurationsExtendsFrom(name: String, script: String, configuration: Project.() -> Unit = {}) {
        newFolder(name).newProjectBuilderProject().run {

            preExistingConfigurations.forEach { name ->
                configurations.register(name)
            }

            configuration()

            eval(script)

            assertThat(
                configurations.names.sorted(),
                hasItems(
                    *expectedConfigurationsExtendsFrom.flatMap {
                        listOf(it.first, it.second)
                    }.sorted().toTypedArray()
                )
            )
            expectedConfigurationsExtendsFrom.forEach { (first, second) ->
                assertThat(configurations[first].extendsFrom, hasItem(configurations[second]))
            }
        }
    }

    @Test
    fun `monomorphic named domain object container api`() {

        assertConfigurationsExtendsFrom("api", """

            val foo = configurations.getByName("foo")
            configurations.getByName("bar") {
                extendsFrom(foo)
            }

            val bazar = configurations.create("bazar")
            configurations.create("cathedral") {
                extendsFrom(bazar)
            }

            val cabin = configurations.named("cabin")
            configurations.named("castle") {
                extendsFrom(cabin.get())
            }

            val valley = configurations.register("valley")
            configurations.register("hill") {
                extendsFrom(valley.get())
            }
        """)
    }

    @Test
    fun `monomorphic named domain object container scope api`() {

        assertConfigurationsExtendsFrom("scope-api", """
            configurations {

                val foo = getByName("foo")
                getByName("bar") {
                    extendsFrom(foo)
                }
    
                val bazar = create("bazar")
                create("cathedral") {
                    extendsFrom(bazar)
                }
    
                val cabin = named("cabin")
                named("castle") {
                    extendsFrom(cabin.get())
                }
    
                val valley = register("valley")
                register("hill") {
                    extendsFrom(valley.get())
                }
            }
        """)
    }

    @Test
    fun `monomorphic named domain object container delegated properties`() {

        assertConfigurationsExtendsFrom("delegated-properties", """

            val foo by configurations.getting
            val bar by configurations.getting {
                extendsFrom(foo)
            }

            val bazar by configurations.creating
            val cathedral by configurations.creating {
                extendsFrom(bazar)
            }

            val cabin by configurations.existing
            val castle by configurations.existing {
                extendsFrom(cabin.get())
            }

            val valley by configurations.registering
            val hill by configurations.registering {
                extendsFrom(valley.get())
            }
        """)
    }

    @Test
    fun `monomorphic named domain object container scope delegated properties`() {

        assertConfigurationsExtendsFrom("scope-delegated-properties", """
            configurations {

                val foo by getting
                val bar by getting {
                    extendsFrom(foo)
                }

                val bazar by creating
                val cathedral by creating {
                    extendsFrom(bazar)
                }

                val cabin by existing
                val castle by existing {
                    extendsFrom(cabin.get())
                }

                val valley by registering
                val hill by registering {
                    extendsFrom(valley.get())
                }
            }
        """)
    }

    @Test
    fun `monomorphic named domain object container scope string invoke`() {

        assertConfigurationsExtendsFrom("scope-string-invoke", """
            configurations {

                val foo = "foo"()
                "bar" {
                    extendsFrom(foo.get())
                }

                val cabin  = "cabin"()
                "castle" {
                    extendsFrom(cabin.get())
                }
            }
        """) {
            configurations {
                val bazar by creating
                val cathedral by creating {
                    extendsFrom(bazar)
                }
                val valley by registering
                val hill by registering {
                    extendsFrom(valley.get())
                }
            }
            apply(plugin = "java")
        }
    }
}
