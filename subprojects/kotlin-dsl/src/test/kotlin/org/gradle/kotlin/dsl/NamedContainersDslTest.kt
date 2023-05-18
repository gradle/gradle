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

import org.gradle.kotlin.dsl.fixtures.AbstractDslTest
import org.gradle.kotlin.dsl.fixtures.runWithProjectBuilderProject

import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class NamedContainersDslTest : AbstractDslTest() {

    @Test
    fun `monomorphic named domain object container api`() {

        testConfigurationContainerVia(
            "api",
            """

            val foo: Configuration = configurations.getByName("foo")
            val bar: Configuration = configurations.getByName("bar") {
                extendsFrom(foo)
            }

            val bazar: Configuration = configurations.create("bazar")
            val cathedral: Configuration = configurations.create("cathedral") {
                extendsFrom(bazar)
            }

            val cabin: NamedDomainObjectProvider<Configuration> = configurations.named("cabin")
            val castle: NamedDomainObjectProvider<Configuration> = configurations.named("castle") {
                extendsFrom(cabin.get())
            }

            val valley: NamedDomainObjectProvider<Configuration> = configurations.register("valley")
            val hill: NamedDomainObjectProvider<Configuration> = configurations.register("hill") {
                extendsFrom(valley.get())
            }
            """
        )
    }

    @Test
    fun `monomorphic named domain object container scope api`() {

        testConfigurationContainerVia(
            "scope-api",
            """
            configurations {

                val foo: Configuration = getByName("foo")
                val bar: Configuration = getByName("bar") {
                    extendsFrom(foo)
                }

                val bazar: Configuration = create("bazar")
                val cathedral: Configuration = create("cathedral") {
                    extendsFrom(bazar)
                }

                val cabin: NamedDomainObjectProvider<Configuration> = named("cabin")
                val castle: NamedDomainObjectProvider<Configuration> = named("castle") {
                    extendsFrom(cabin.get())
                }

                val valley: NamedDomainObjectProvider<Configuration> = register("valley")
                val hill: NamedDomainObjectProvider<Configuration> = register("hill") {
                    extendsFrom(valley.get())
                }
            }
            """
        )
    }

    @Test
    fun `monomorphic named domain object container delegated properties`() {

        testConfigurationContainerVia(
            "delegated-properties",
            """

            val foo: Configuration by configurations.getting
            val bar: Configuration by configurations.getting {
                extendsFrom(foo)
            }

            val bazar: Configuration by configurations.creating
            val cathedral: Configuration by configurations.creating {
                extendsFrom(bazar)
            }

            val cabin: NamedDomainObjectProvider<Configuration> by configurations.existing
            val castle: NamedDomainObjectProvider<Configuration> by configurations.existing {
                extendsFrom(cabin.get())
            }

            val valley: NamedDomainObjectProvider<Configuration> by configurations.registering
            val hill: NamedDomainObjectProvider<Configuration> by configurations.registering {
                extendsFrom(valley.get())
            }
            """
        )
    }

    @Test
    fun `monomorphic named domain object container scope delegated properties`() {

        testConfigurationContainerVia(
            "scope-delegated-properties",
            """
            configurations {

                val foo: Configuration by getting
                val bar: Configuration by getting {
                    extendsFrom(foo)
                }

                val bazar: Configuration by creating
                val cathedral: Configuration by creating {
                    extendsFrom(bazar)
                }

                val cabin: NamedDomainObjectProvider<Configuration> by existing
                val castle: NamedDomainObjectProvider<Configuration> by existing {
                    extendsFrom(cabin.get())
                }

                val valley: NamedDomainObjectProvider<Configuration> by registering
                val hill: NamedDomainObjectProvider<Configuration> by registering {
                    extendsFrom(valley.get())
                }
            }
            """
        )
    }

    @Test
    fun `monomorphic named domain object container scope string invoke`() {

        testConfigurationContainerVia(
            "scope-string-invoke",
            """
            configurations {

                val foo: NamedDomainObjectProvider<Configuration> = "foo"()
                val bar: NamedDomainObjectProvider<Configuration> = "bar" {
                    extendsFrom(foo.get())
                }

                val cabin: NamedDomainObjectProvider<Configuration>  = "cabin"()
                val castle: NamedDomainObjectProvider<Configuration> = "castle" {
                    extendsFrom(cabin.get())
                }
            }
            """
        ) {
            configurations {
                val bazar by creating
                create("cathedral") {
                    extendsFrom(bazar)
                }
                val valley by registering
                register("hill") {
                    extendsFrom(valley.get())
                }
            }
            apply(plugin = "java")
        }
    }

    private
    fun testConfigurationContainerVia(name: String, script: String, configuration: Project.() -> Unit = {}) {
        runWithProjectBuilderProject(newFolder(name)) {
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
}
