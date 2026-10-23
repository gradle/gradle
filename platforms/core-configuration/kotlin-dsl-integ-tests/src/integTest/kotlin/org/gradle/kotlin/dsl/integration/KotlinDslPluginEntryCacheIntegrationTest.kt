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
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.junit.Assert.assertTrue
import org.junit.Test
import spock.lang.Issue


@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class KotlinDslPluginEntryCacheIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    @Issue("https://github.com/gradle/gradle/issues/39441")
    fun `plugin entries cache is closed after build finishes`() {
        withKotlinBuildSrc()
        withFile("buildSrc/src/main/kotlin/my-plugin.gradle.kts", "")
        withBuildScript("""plugins { id("my-plugin") }""")
        withFile("other/settings.gradle")
        withFile(
            "other/build.gradle",
            """
            def workers = Thread.allStackTraces.keySet().count { it.name.contains("Cache worker for Kotlin DSL Plugin Entries") }
            println("plugin entries cache workers: " + workers)
            """
        )

        executer.requireDaemon().requireIsolatedDaemons()
        build("help")

        val cacheDir = projectRoot.resolve(".gradle").walk().single { it.isDirectory && it.name == "kotlin-dsl-plugin-entries" }
        assertTrue("cache files can be deleted", cacheDir.deleteRecursively())

        build(projectRoot.resolve("other"), "help")
            .assertOutputContains("plugin entries cache workers: 0")
    }
}
