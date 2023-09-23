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

package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinDslPluginsIntegrationTest
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.junit.Test
import spock.lang.Issue


@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class ProjectSchemaLambdaAccessorsIntegrationTest : AbstractKotlinDslPluginsIntegrationTest() {
    @Test
    fun `accessors to __untyped__ groovy closures extensions are typed Any`() {

        withDefaultSettings()
        PluginBuilder(file("buildSrc")).apply {
            addPlugin(
                """
                project.extensions.add("closureExtension", { String name ->
                    name.toUpperCase()
                })
                """.trimIndent(),
                "my"
            )
            generateForBuildSrc()
        }

        withBuildScript(
            """
            plugins {
                my
            }

            inline fun <reified T> typeOf(value: T) = typeOf<T>()

            println("closureExtension: " + typeOf(closureExtension))

            val casted = closureExtension as groovy.lang.Closure<*>
            println(casted.call("some"))
            """
        )

        build("help").apply {
            assertOutputContains("closureExtension: java.lang.Object")
            assertOutputContains("SOME")
        }
    }

    @Test
    fun `accessors to __untyped__ kotlin lambda extensions are typed Any`() {

        withDefaultSettings()
        withKotlinBuildSrc()
        withFile(
            "buildSrc/src/main/kotlin/my.gradle.kts",
            """
            extensions.add("lambdaExtension", { name: String ->
                name.toUpperCase()
            })
            """
        )

        withBuildScript(
            """
            plugins {
                my
            }

            inline fun <reified T> typeOf(value: T) = typeOf<T>()

            println("lambdaExtension: " + typeOf(lambdaExtension))

            val casted = lambdaExtension as (String) -> String
            println(casted.invoke("some"))
            """
        )

        build("help").apply {
            assertOutputContains("lambdaExtension: java.lang.Object")
            assertOutputContains("SOME")
        }
    }

    @Test
    fun `accessors to __untyped__ java lambda extensions are typed Any`() {

        withDefaultSettings()
        withFile(
            "buildSrc/build.gradle",
            """
            plugins {
                id("java")
                id("java-gradle-plugin")
            }
            gradlePlugin {
                plugins {
                    my {
                        id = "my"
                        implementationClass = "my.MyPlugin"
                    }
                }
            }
            """
        )
        withFile(
            "buildSrc/src/main/java/my/MyPlugin.java",
            """
            package my;

            import org.gradle.api.*;
            import java.util.function.Function;

            public class MyPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    Function<String, String> lambda = s -> s.toUpperCase();
                    project.getExtensions().add("lambdaExtension", lambda);
                }
            }
            """
        )

        withBuildScript(
            """
            import java.util.function.Function

            plugins {
                my
            }

            inline fun <reified T> typeOf(value: T) = typeOf<T>()

            println("lambdaExtension: " + typeOf(lambdaExtension))

            val casted = lambdaExtension as Function<String, String>
            println(casted.apply("some"))
            """
        )

        build("help").apply {
            assertOutputContains("lambdaExtension: java.lang.Object")
            assertOutputContains("SOME")
        }
    }

    @Test
    fun `accessors to __typed__ groovy closures extensions are typed`() {

        withDefaultSettings()
        PluginBuilder(file("buildSrc")).apply {
            addPlugin(
                """
                def typeToken = new org.gradle.api.reflect.TypeOf<Closure<String>>() {}
                project.extensions.add(typeToken, "closureExtension", { String name ->
                    name.toUpperCase()
                })
                """.trimIndent(),
                "my"
            )
            generateForBuildSrc()
        }

        withBuildScript(
            """
            plugins {
                my
            }

            inline fun <reified T> typeOf(value: T) = typeOf<T>()

            println("closureExtension: " + typeOf(closureExtension))

            println(closureExtension.call("some"))
            """
        )

        build("help").apply {
            assertOutputContains("closureExtension: groovy.lang.Closure<java.lang.String>")
            assertOutputContains("SOME")
        }
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/10772")
    fun `accessors to __typed__ kotlin lambda extensions are typed`() {

        withDefaultSettings()
        withKotlinBuildSrc()
        withFile(
            "buildSrc/src/main/kotlin/my.gradle.kts",
            """
            val typeToken = typeOf<(String) -> String>()
            val lambda = { name: String -> name.toUpperCase() }
            extensions.add(typeToken, "lambdaExtension", lambda)
            """
        )

        withBuildScript(
            """
            plugins {
                my
            }

            inline fun <reified T> typeOf(value: T) = typeOf<T>()

            println("lambdaExtension: " + typeOf(lambdaExtension))

            println(lambdaExtension("some"))
            """
        )

        build("help").apply {
            assertOutputContains("lambdaExtension: kotlin.jvm.functions.Function1<? super java.lang.String, ? extends java.lang.String>")
            assertOutputContains("SOME")
        }
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/10771")
    fun `accessors to __typed__ java lambda extensions are typed`() {

        withDefaultSettings()
        withFile(
            "buildSrc/build.gradle",
            """
            plugins {
                id("java")
                id("java-gradle-plugin")
            }
            gradlePlugin {
                plugins {
                    my {
                        id = "my"
                        implementationClass = "my.MyPlugin"
                    }
                }
            }
            """
        )
        withFile(
            "buildSrc/src/main/java/my/MyPlugin.java",
            """
            package my;

            import org.gradle.api.*;
            import org.gradle.api.reflect.*;
            import java.util.function.Function;

            public class MyPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    TypeOf<Function<String, String>> typeToken = new TypeOf<Function<String, String>>() {};
                    Function<String, String> lambda = s -> s.toUpperCase();
                    project.getExtensions().add(typeToken, "lambdaExtension", lambda);
                }
            }
            """
        )

        withBuildScript(
            """
            import java.util.function.Function

            plugins {
                my
            }

            inline fun <reified T> typeOf(value: T) = typeOf<T>()

            println("lambdaExtension: " + typeOf(lambdaExtension))

            val casted = lambdaExtension as Function<String, String>
            println(casted.apply("some"))
            """
        )

        build("help").apply {
            assertOutputContains("lambdaExtension: java.util.function.Function<java.lang.String, java.lang.String>")
            assertOutputContains("SOME")
        }
    }
}
