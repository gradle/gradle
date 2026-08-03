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

package gradlebuild.idea.tasks

import gradlebuild.basics.createSecureDocumentBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.w3c.dom.Element
import java.io.File


/**
 * Verifies that the project SDK committed in `.idea/misc.xml` can support the project language
 * level committed next to it.
 *
 * IntelliJ derives the language level from the Gradle model and rewrites it on sync, but it does
 * not derive `project-jdk-name`: that is a reference into the developer's global SDK table, so it
 * can silently fall behind a language level bump and make IntelliJ report
 * "Module JDK is misconfigured" after import.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class CheckIdeaJdkConfiguration : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val ideaMiscXml: RegularFileProperty

    @TaskAction
    fun check() {
        val miscXml = ideaMiscXml.get().asFile
        val projectRootManager = readProjectRootManager(miscXml)

        val languageLevel = projectRootManager.getAttribute("languageLevel")
        val jdkName = projectRootManager.getAttribute("project-jdk-name")

        val requiredVersion = parseLanguageLevel(languageLevel)
            ?: throw GradleException("Cannot parse languageLevel '$languageLevel' in $miscXml")

        // 'project-jdk-name' is a free-form label into the developer's SDK table, so a version can
        // only be inferred by convention. If there is none to infer, there is nothing to verify.
        val jdkVersion = parseJdkName(jdkName)
        if (jdkVersion == null) {
            logger.info("Cannot infer a Java version from project-jdk-name '$jdkName', skipping check")
            return
        }

        if (jdkVersion < requiredVersion) {
            throw GradleException(
                "${miscXml.name} declares languageLevel '$languageLevel' but project-jdk-name '$jdkName', " +
                    "which is Java $jdkVersion. IntelliJ reports \"Module JDK is misconfigured\" after import " +
                    "when the project SDK cannot support the project language level.\n\n" +
                    "Update the 'ProjectRootManager' component in ${miscXml.path}."
            )
        }
    }

    private
    fun readProjectRootManager(miscXml: File): Element =
        createSecureDocumentBuilder()
            .parse(miscXml).documentElement
            .getElementsByTagName("component")
            .let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }
            .singleOrNull { it.getAttribute("name") == "ProjectRootManager" }
            ?: throw GradleException("No single 'ProjectRootManager' component in $miscXml")

    /**
     * Handles the language level constants IDEA writes, e.g. `JDK_25`, `JDK_1_8`, `JDK_25_PREVIEW`.
     */
    private
    fun parseLanguageLevel(value: String): Int? =
        value.removePrefix("JDK_")
            .removeSuffix("_PREVIEW")
            .split("_")
            .mapNotNull { it.toIntOrNull() }
            .lastOrNull()

    /**
     * Handles the SDK name this repository commits (`25`) and, best-effort, names a developer may
     * have locally instead, like `temurin-25`.
     */
    private
    fun parseJdkName(value: String): Int? =
        value.split(Regex("[^0-9]+"))
            .lastOrNull { it.isNotEmpty() }
            ?.toIntOrNull()
}
