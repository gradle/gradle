/*
 * Copyright 2023 the original author or authors.
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
import org.junit.Test


@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class PrecompiledScriptPluginVersionCatalogIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `version catalogs main extension is available`() {
        withKotlinBuildSrc()
        withSimpleVersionCatalog()
        val scriptBody = """
            // Can use outer build version catalog when applied
            println(versionCatalogs.named("libs").findLibrary("groovy").get().get())
        """
        withFile("buildSrc/src/main/kotlin/plugin-without-plugins.gradle.kts", scriptBody)
        withFile(
            "buildSrc/src/main/kotlin/plugin-with-plugins.gradle.kts",
            """
            plugins { id("base") }
            $scriptBody
            """
        )
        withBuildScript(
            """
                plugins {
                    id("plugin-without-plugins")
                    id("plugin-with-plugins")
                }
            """
        )

        build(":help").apply {
            assertOutputContains("org.codehaus.groovy:groovy:3.0.5")
        }
    }

    @Test
    fun `version catalogs from outer builds are not available as accessors`() {
        withKotlinBuildSrc()
        withSimpleVersionCatalog()
        withFile("buildSrc/src/main/kotlin/plugin-without-plugins.gradle.kts", "println(libs)")
        withFile(
            "buildSrc/src/main/kotlin/plugin-with-plugins.gradle.kts",
            """
            plugins { id("base") }
            println(libs)
            """
        )
        withBuildScript(
            """
            plugins {
                id("plugin-without-plugins")
                id("plugin-with-plugins")
            }
            """
        )

        buildAndFail(":help").apply {
            assertHasFailure("Execution failed for task ':buildSrc:compileKotlin'.") {
                assertHasErrorOutput("buildSrc/src/main/kotlin/plugin-without-plugins.gradle.kts:1:9 Unresolved reference: libs")
                assertHasErrorOutput("buildSrc/src/main/kotlin/plugin-with-plugins.gradle.kts:3:21 Unresolved reference: libs")
            }
        }
    }

    private
    fun withSimpleVersionCatalog() {
        withFile(
            "gradle/libs.versions.toml",
            """
            [libraries]
            groovy = "org.codehaus.groovy:groovy:3.0.5"
            """
        )
    }
}
