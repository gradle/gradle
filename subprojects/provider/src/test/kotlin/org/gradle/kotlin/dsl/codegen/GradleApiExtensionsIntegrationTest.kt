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

package org.gradle.kotlin.dsl.codegen

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest

import org.junit.Test


class GradleApiExtensionsIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `can use Gradle API generated extensions in scripts`() {

        withFile("init.gradle.kts", """
            allprojects {
                container(String::class)
            }
        """)

        withSettings("""
            buildCache {
                local(DirectoryBuildCache::class)
            }
            sourceControl {
                vcsMappings {
                    withModule("some:thing") {
                        from(GitVersionControlSpec::class) {
                            url = uri("")
                        }
                    }
                }
            }
        """)

        withBuildScript("""
            container(String::class)
            apply(from = "plugin.gradle.kts")
        """)

        withFile("plugin.gradle.kts", """
            import org.apache.tools.ant.filters.ReplaceTokens

            // Class<T> to KClass<T>
            property(Long::class)

            // Groovy named arguments to vararg of Pair
            fileTree("dir" to "src", "excludes" to listOf("**/ignore/**", "**/.data/**"))

            // Class<T> + Action<T> to KClass<T> + T.() -> Unit
            tasks.register("foo", Copy::class) {
                from("src")
                into("dst")

                // Class<T> + Groovy named arguments to KClass<T> + vararg of Pair
                filter(ReplaceTokens::class, "foo" to "bar")
            }
        """)

        build("foo", "-I", "init.gradle.kts")
    }

    @Test
    fun `can use Gradle API generated extensions in buildSrc`() {

        withBuildScriptIn("buildSrc", """
            plugins {
                `kotlin-dsl`
                `java-gradle-plugin`
            }
            apply<org.gradle.kotlin.dsl.plugins.precompiled.PrecompiledScriptPlugins>()
        """)

        withFile("buildSrc/src/main/kotlin/foo/FooTask.kt", """
            package foo

            import org.gradle.api.*
            import org.gradle.api.tasks.*

            import org.gradle.kotlin.gradle.ext.*

            import org.apache.tools.ant.filters.ReplaceTokens

            open class FooTask : DefaultTask() {

                @TaskAction
                fun foo() = project.run {
                    container(Long::class)
                }
            }
        """)

        withFile("buildSrc/src/main/kotlin/foo/foo.gradle.kts", """
            package foo

            tasks.register("foo", FooTask::class)
        """)

        withBuildScript("""
            plugins {
                id("foo.foo")
            }
        """)

        build("foo")
    }
}
