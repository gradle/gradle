/*
 * Copyright 2026 the original author or authors.
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
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertFalse
import org.junit.Test
import spock.lang.Issue


@Issue("https://github.com/gradle/gradle/issues/39131")
class ScriptServiceLookupIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `all documented services can be looked up in a build script`() {
        withDefaultSettings()
        withBuildScript("""
            val services = listOf(
                service<ObjectFactory>(),
                service<ProviderFactory>(),
                service<FileSystemOperations>(),
                service<ArchiveOperations>(),
                service<ProjectLayout>(),
            )
            println("resolved services: " + services.size)
        """)

        assertThat(build("help").output, containsString("resolved services: 5"))
    }

    @Test
    fun `can delete files with FileSystemOperations looked up inside a task action`() {
        withDefaultSettings()
        withFile("thing.txt", "content")
        withBuildScript("""
            tasks.register("cleanThing") {
                doLast {
                    service<FileSystemOperations>().delete {
                        delete("thing.txt")
                    }
                }
            }
        """)

        build("cleanThing")

        assertFalse(existing("thing.txt").exists())
    }

    @Test
    fun `a task block in a precompiled script plugin can look up services`() {
        withDefaultSettings().appendText("""include("consumer")""")
        withKotlinDslPluginIn("buildSrc")
        withDefaultSettingsIn("buildSrc")
        withFile("buildSrc/src/main/kotlin/my-task-conventions.gradle.kts", """
            tasks.register("cleanThing") {
                doLast {
                    service<FileSystemOperations>().delete {
                        delete("thing.txt")
                    }
                }
            }
        """)
        withFile("consumer/thing.txt", "content")
        withBuildScriptIn("consumer", """
            plugins {
                id("my-task-conventions")
            }
        """)
        withBuildScript("")

        build(":consumer:cleanThing")

        assertFalse(existing("consumer/thing.txt").exists())
    }

    @Test
    fun `looking up a settings-only service from a task in a build script does not compile`() {
        withDefaultSettings()
        withBuildScript("""
            tasks.register("useLayout") {
                service<BuildLayout>()
            }
        """)

        buildAndFail("useLayout").apply {
            assertHasErrorOutput("Script compilation error")
        }
    }

    @Test
    fun `can capture a service at configuration time and use it in a task action`() {
        withDefaultSettings()
        withFile("thing.txt", "content")
        withBuildScript("""
            // A top-level `val` is a property of the script, which a task action must not capture
            service<FileSystemOperations>().let { fs ->
                tasks.register("cleanThing") {
                    doLast {
                        fs.delete {
                            delete("thing.txt")
                        }
                    }
                }
            }
        """)

        build("cleanThing")

        assertFalse(existing("thing.txt").exists())
    }

    @Test
    fun `inside a subprojects block the lookup is still the one of the script`() {
        withDefaultSettings().appendText("""include("a")""")
        withFolders { "a" {} }
        withBuildScript("""
            subprojects {
                // A project has no service lookup, so this is the lookup of the root build script.
                // Use `layout` to get the layout of the subproject.
                println("service layout for " + path + ": " + service<ProjectLayout>().projectDirectory.asFile.name)
            }
        """)

        assertThat(build("help").output, containsString("service layout for :a: " + projectRoot.name))
    }

    @Test
    fun `services can be looked up in a settings script`() {
        withSettings("""
            val layout = service<BuildLayout>()
            println("settings dir name: " + layout.settingsDirectory.asFile.name)

            val property = service<ObjectFactory>().property(String::class.java)
            property.set("from-settings")
            println("settings property: " + property.get())
        """)
        withBuildScript("")

        val output = build("help").output

        assertThat(output, containsString("settings dir name: " + projectRoot.name))
        assertThat(output, containsString("settings property: from-settings"))
    }

    @Test
    fun `services can be looked up in an init script`() {
        withDefaultSettings()
        withBuildScript("")
        withFile("init.gradle.kts", """
            val property = service<ObjectFactory>().property(String::class.java)
            property.set("from-init")
            println("init property: " + property.get())
        """)

        assertThat(build("help", "-I", "init.gradle.kts").output, containsString("init property: from-init"))
    }

    @Test
    fun `services can be looked up via the Class overload`() {
        withDefaultSettings()
        withBuildScript("""
            val objectFactory = service(ObjectFactory::class.java)
            println("class lookup: " + (objectFactory is ObjectFactory))
        """)

        assertThat(build("help").output, containsString("class lookup: true"))
    }

    @Test
    fun `services can be looked up in a precompiled script plugin`() {
        withDefaultSettings().appendText("""include("consumer")""")
        withKotlinDslPluginIn("buildSrc")
        withDefaultSettingsIn("buildSrc")
        withFile("buildSrc/src/main/kotlin/my-conventions.gradle.kts", """
            service<FileSystemOperations>().let { fs ->
                tasks.register("cleanThing") {
                    doLast {
                        fs.delete {
                            delete("thing.txt")
                        }
                    }
                }
            }
        """)
        withFile("consumer/thing.txt", "content")
        withBuildScriptIn("consumer", """
            plugins {
                id("my-conventions")
            }
        """)
        withBuildScript("")

        build(":consumer:cleanThing")

        assertFalse(existing("consumer/thing.txt").exists())
    }

    @Test
    fun `service lookup is not available on the Gradle model object`() {
        withSettings("""
            gradle.service<ObjectFactory>()
        """)
        withBuildScript("")

        buildAndFail("help").apply {
            assertHasErrorOutput("Script compilation error")
            assertHasErrorOutput("receiver type mismatch")
        }
    }

    @Test
    fun `looking up an internal service does not compile`() {
        // A type that implements none of the scope markers is rejected by the bound at compile time.
        // The runtime message that enumerates the available services is covered by the Groovy DSL test.
        withDefaultSettings()
        withBuildScript("""
            service<org.gradle.api.internal.project.ProjectInternal>()
        """)

        buildAndFail("help").apply {
            assertHasErrorOutput("Script compilation error")
        }
    }

    @Test
    fun `looking up a project-only service from a settings script does not compile`() {
        withSettings("""
            service<ProjectLayout>()
        """)
        withBuildScript("")

        buildAndFail("help").apply {
            assertHasErrorOutput("Script compilation error")
            assertHasErrorOutput("SettingsService")
        }
    }

    @Test
    fun `looking up a settings-only service from a build script does not compile`() {
        withDefaultSettings()
        withBuildScript("""
            service<BuildLayout>()
        """)

        buildAndFail("help").apply {
            assertHasErrorOutput("Script compilation error")
            assertHasErrorOutput("ProjectService")
        }
    }
}
