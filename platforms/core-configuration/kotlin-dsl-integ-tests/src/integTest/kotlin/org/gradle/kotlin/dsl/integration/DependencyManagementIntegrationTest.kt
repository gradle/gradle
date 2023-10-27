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

package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.fixtures.containsMultiLineString
import org.gradle.kotlin.dsl.fixtures.normalisedPath

import org.hamcrest.CoreMatchers.containsString

import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import spock.lang.Issue


class DependencyManagementIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `declare dependency constraints`() {

        withFile("repo/in-block/accessor-1.0.jar")
        withFile("repo/in-block/accessor-with-action-1.0.jar")
        withFile("repo/in-block/string-invoke-1.0.jar")
        withFile("repo/in-block/string-invoke-with-action-1.0.jar")
        withFile("repo/direct/accessor-1.0.jar")
        withFile("repo/direct/accessor-with-action-1.0.jar")
        withFile("repo/direct-block/string-invoke-1.0.jar")
        withFile("repo/direct-block/string-invoke-with-action-1.0.jar")

        withBuildScript(
            """
            plugins {
                `java-library`
            }

            // Declare some dependencies

            dependencies {
                api("in-block:accessor")
                api("in-block:accessor-with-action")
                api("in-block:string-invoke")
                api("in-block:string-invoke-with-action")
                api("direct:accessor")
                api("direct:accessor-with-action")
                api("direct-block:string-invoke")
                api("direct-block:string-invoke-with-action")
            }

            // Declare dependency constraints

            dependencies {
                constraints {
                    api("in-block:accessor:1.0")
                    api("in-block:accessor-with-action") {
                        version { strictly("1.0") }
                    }
                }
                (constraints) {
                    "api"("in-block:string-invoke:1.0")
                    "api"("in-block:string-invoke-with-action") {
                        version { strictly("1.0") }
                    }
                }
            }
            dependencies.constraints.api("direct:accessor:1.0")
            dependencies.constraints.api("direct:accessor-with-action") {
                version { strictly("1.0") }
            }
            (dependencies.constraints) {
                "api"("direct-block:string-invoke:1.0")
                "api"("direct-block:string-invoke-with-action") {
                    version { strictly("1.0") }
                }
            }

            repositories {
                ivy {
                    url = uri("${existing("repo").normalisedPath}")
                    patternLayout {
                        artifact("[organisation]/[module]-[revision].[ext]")
                    }
                }
            }
            """
        )

        build("dependencies", "--configuration", "api").apply {
            assertThat(
                output,
                containsMultiLineString(
                    """
                api - API dependencies for the 'main' feature. (n)
                +--- in-block:accessor (n)
                +--- in-block:accessor-with-action (n)
                +--- in-block:string-invoke (n)
                +--- in-block:string-invoke-with-action (n)
                +--- direct:accessor (n)
                +--- direct:accessor-with-action (n)
                +--- direct-block:string-invoke (n)
                \--- direct-block:string-invoke-with-action (n)
                """
                )
            )
        }

        listOf(
            "in-block:accessor",
            "in-block:accessor-with-action",
            "in-block:string-invoke",
            "in-block:string-invoke-with-action",
            "direct:accessor",
            "direct:accessor-with-action",
            "direct-block:string-invoke",
            "direct-block:string-invoke-with-action"
        ).forEach { dep ->
            build("dependencyInsight", "--configuration", "compileClasspath", "--dependency", dep).apply {
                assertThat(output, containsString("$dep:1.0 (by constraint)"))
            }
        }
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/26601")
    fun `can use dependencyScope configuration provider in dependencies block`() {

        withFile("repo/in-block/accessor-1.0.jar")

        withBuildScript(
            """
            val foo by configurations.dependencyScope
            val bar by configurations.dependencyScope { }
            
            dependencies {
                foo("in-block:accessor:1.0")
                bar("in-block:accessor:1.0")
            }
            
            repositories {
                ivy {
                    url = uri("${existing("repo").normalisedPath}")
                    patternLayout {
                        artifact("[organisation]/[module]-[revision].[ext]")
                    }
                }
            }
            """
        )

        build("dependencies", "--configuration", "foo").apply {
            assertThat(
                output,
                containsMultiLineString(
                    """
                foo (n)
                \--- in-block:accessor:1.0 (n)
                """
                )
            )
        }
        build("dependencies", "--configuration", "bar").apply {
            assertThat(
                output,
                containsMultiLineString(
                    """
                bar (n)
                \--- in-block:accessor:1.0 (n)
                """
                )
            )
        }
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/26602")
    fun `can use dependencyScope configuration invoke in dependencies block`() {

        withFile("repo/in-block/accessor-1.0.jar")

        withBuildScript(
            """
            val foo = configurations.dependencyScope("foo")
            val bar = configurations.dependencyScope("bar") { }
            
            dependencies {
                foo("in-block:accessor:1.0")
                bar("in-block:accessor:1.0")
            }
            
            repositories {
                ivy {
                    url = uri("${existing("repo").normalisedPath}")
                    patternLayout {
                        artifact("[organisation]/[module]-[revision].[ext]")
                    }
                }
            }
            """
        )

        build("dependencies", "--configuration", "foo").apply {
            assertThat(
                output,
                containsMultiLineString(
                    """
                foo (n)
                \--- in-block:accessor:1.0 (n)
                """
                )
            )
        }
        build("dependencies", "--configuration", "bar").apply {
            assertThat(
                output,
                containsMultiLineString(
                    """
                bar (n)
                \--- in-block:accessor:1.0 (n)
                """
                )
            )
        }
    }
}
