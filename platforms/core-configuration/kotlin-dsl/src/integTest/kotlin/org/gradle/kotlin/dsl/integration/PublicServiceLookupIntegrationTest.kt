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


@Issue("https://github.com/gradle/gradle/issues/13121")
class PublicServiceLookupIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `all documented services can be looked up in a build script`() {
        withDefaultSettings()
        withBuildScript("""
            val services = listOf(
                service<ObjectFactory>(),
                service<ProviderFactory>(),
                service<FileSystemOperations>(),
                service<ArchiveOperations>(),
                service<ExecOperations>(),
                service<ProjectLayout>(),
            )
            println("resolved services: " + services.size)
        """)

        assertThat(build("help").output, containsString("resolved services: 6"))
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
    fun `can capture a service at configuration time and use it in a task action`() {
        withDefaultSettings()
        withFile("thing.txt", "content")
        withBuildScript("""
            tasks.register("cleanThing") {
                val fs = service<FileSystemOperations>()
                doLast {
                    fs.delete {
                        delete("thing.txt")
                    }
                }
            }
        """)

        build("cleanThing")

        assertFalse(existing("thing.txt").exists())
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
    fun `services can be looked up via the generated KClass overload`() {
        withDefaultSettings()
        withBuildScript("""
            val execOps = service(ExecOperations::class)
            println("kclass lookup: " + (execOps is ExecOperations))
        """)

        assertThat(build("help").output, containsString("kclass lookup: true"))
    }

    @Test
    fun `services can be looked up in a precompiled script plugin`() {
        withDefaultSettings().appendText("""include("consumer")""")
        withKotlinDslPluginIn("buildSrc")
        withDefaultSettingsIn("buildSrc")
        withFile("buildSrc/src/main/kotlin/my-conventions.gradle.kts", """
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
                id("my-conventions")
            }
        """)
        withBuildScript("")

        build(":consumer:cleanThing")

        assertFalse(existing("consumer/thing.txt").exists())
    }

    @Test
    fun `looking up an internal service fails and enumerates the available services`() {
        withDefaultSettings()
        withBuildScript("""
            service<org.gradle.api.internal.project.ProjectInternal>()
        """)

        val failure = buildAndFail("help")

        failure.assertHasDescription(
            "org.gradle.api.internal.project.ProjectInternal is not a service that is available for lookup with service(). " +
                "The following services are available in project scripts and plugins: " +
                "org.gradle.api.model.ObjectFactory, org.gradle.api.provider.ProviderFactory, " +
                "org.gradle.api.file.FileSystemOperations, org.gradle.api.file.ArchiveOperations, " +
                "org.gradle.process.ExecOperations, org.gradle.api.file.ProjectLayout."
        )
    }

    @Test
    fun `looking up a project-only service from a settings script fails with a helpful message`() {
        withSettings("""
            service<ProjectLayout>()
        """)
        withBuildScript("")

        val failure = buildAndFail("help")

        failure.assertHasDescription(
            "org.gradle.api.file.ProjectLayout is not available in settings scripts and plugins. " +
                "It is available in project scripts and plugins and tasks."
        )
    }
}
