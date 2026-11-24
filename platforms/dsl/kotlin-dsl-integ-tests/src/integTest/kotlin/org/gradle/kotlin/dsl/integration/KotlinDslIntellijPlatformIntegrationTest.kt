/*
 * Copyright 2025 the original author or authors.
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
import org.junit.Test
import spock.lang.Issue
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Starting with IntelliJ 2021.2, the IntelliJ Platform uses a regex to select JARs from the Gradle
 * distribution in order to class load the Kotlin DSL script templates.
 * This test ensures that we don't break this mechanism by e.g. moving classes around.
 * If you see this test failing, it means that some necessary classes have been moved to modules that
 * are not covered by the regexes used by the IntelliJ Platform.
 * IntelliJ should instead use our `GradleDslBaseScriptModel` TAPI model that provides the right
 * information for loading the Kotlin DSL script templates.
 * At the time of writing, this is scheduled for IJ 2026.1.
 *
 * See the IntelliJ code at:
 * https://github.com/JetBrains/intellij-community/blob/81d015cd15446a2722023e6106aeb7d00b2fe79a/plugins/kotlin/gradle/scripting/kotlin.gradle.scripting.shared/src/org/jetbrains/kotlin/gradle/scripting/shared/gradleScriptDefinitionsUtils.kt#L197
 */
@Issue("https://github.com/gradle/gradle/issues/34679")
class KotlinDslIntellijPlatformIntegrationTest : AbstractKotlinIntegrationTest() {

    private val intellij2021dot2 = Regex("^gradle-(?:kotlin-dsl|core).*\\.jar\$")
    private val intellij2025dot2 = Regex("^gradle-(?:kotlin-dsl|core|base-services).*\\.jar\$")

    @Test
    fun `can load script templates from distro using intellij platform 2021_2 legacy regex`() {
        loadScriptTemplatesFromDistro(intellij2021dot2)
    }

    @Test
    fun `can load script templates from distro using intellij platform 2025_2 legacy regex`() {
        loadScriptTemplatesFromDistro(intellij2025dot2)
    }

    @Suppress("DEPRECATION")
    private
    val templateClassNames = listOf(
        // Script templates for IDE support
        org.gradle.kotlin.dsl.KotlinGradleScriptTemplate::class.java.name,
        org.gradle.kotlin.dsl.KotlinSettingsScriptTemplate::class.java.name,
        org.gradle.kotlin.dsl.KotlinProjectScriptTemplate::class.java.name,
        // Legacy script templates for IDE support
        org.gradle.kotlin.dsl.KotlinInitScript::class.java.name,
        org.gradle.kotlin.dsl.KotlinSettingsScript::class.java.name,
        org.gradle.kotlin.dsl.KotlinBuildScript::class.java.name,
        // Legacy script dependencies resolver for IDE support
        org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependenciesResolver::class.java.name,
    )

    private fun loadScriptTemplatesFromDistro(ijRegex: Regex) {
        val installDir = buildContext.distribution(buildContext.version.version).gradleHomeDir.toPath()
        val libDir = installDir.resolve("lib")
        val selected = Files.newDirectoryStream(libDir).use { stream ->
            stream.filter { path -> path.fileName.toString().matches(ijRegex) }.toList()
        }
        loadClassesFrom(selected, templateClassNames)
    }

    private fun loadClassesFrom(classPath: List<Path>, classNames: List<String>) {
        classLoaderFor(classPath).use { loader ->
            classNames.forEach {
                loader.loadClass(it)
            }
        }
    }

    private fun classLoaderFor(classPath: List<Path>): URLClassLoader =
        URLClassLoader(classPath.map { it.toFile().toURI().toURL() }.toTypedArray())
}
