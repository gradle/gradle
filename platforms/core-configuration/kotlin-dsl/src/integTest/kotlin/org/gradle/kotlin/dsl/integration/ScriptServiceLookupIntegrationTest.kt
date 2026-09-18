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
import org.junit.Assert.assertFalse
import org.junit.Test
import spock.lang.Issue


@Issue("https://github.com/gradle/gradle/issues/39131")
class ScriptServiceLookupIntegrationTest : AbstractKotlinIntegrationTest() {

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
}
