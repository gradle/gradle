/*
 * Copyright 2021 the original author or authors.
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
import org.gradle.api.internal.StartParameterInternal
import org.gradle.initialization.StartParameterBuildOptions

val startParameterInternal =
    gradle.startParameter as StartParameterInternal

val isConfigurationCacheEnabled: Boolean =
    startParameterInternal.configurationCache.get()

val isConfigurationCacheProblemsFail: Boolean =
    startParameterInternal.configurationCacheProblems == StartParameterBuildOptions.ConfigurationCacheProblemsOption.Value.FAIL

if (isConfigurationCacheEnabled && isConfigurationCacheProblemsFail) {

    val unsupportedTasksPredicate: (Task) -> Boolean = { task: Task ->
        when {

            // Working tasks that would otherwise be matched by filters below
            task.name in listOf("publishLocalPublicationToLocalRepository", "validateExternalPlugins") -> false
            task.name.startsWith("validatePluginWithId") -> false

            // Core tasks
            task.name in listOf(
                "buildEnvironment",
                "dependencies",
                "dependencyInsight",
                "properties",
                "projects",
                "kotlinDslAccessorsReport",
                "outgoingVariants",
                "javaToolchains",
                "components",
                "dependantComponents",
                "model",
            ) -> true
            task.name.startsWith("publish") -> true
            task.name.startsWith("idea") -> true

            // gradle/gradle build tasks
            task.name in listOf(
                "updateInitPluginTemplateVersionFile",
                "buildshipEclipseProject",
                "resolveAllDependencies",
            ) -> true
            task.name.endsWith("Wrapper") -> true
            task.path.startsWith(":docs") -> {
                when {
                    task.name in listOf("docs", "stageDocs", "docsTest", "serveDocs") -> true
                    task.name.startsWith("userguide") -> true
                    task.name.contains("Sample") -> true
                    task.name.contains("Snippet") -> true
                    else -> false
                }
            }
            task.path.startsWith(":performance") -> true
            task.path.startsWith(":build-scan-performance") -> true
            task.path.startsWith(":internal-android-performance-testing") -> true

            // Third parties tasks

            // Publish plugin
            task.name == "login" -> true

            // Kotlin/JS
            task.name in listOf("generateExternals") -> true

            // JMH plugin
            task.name in listOf("jmh", "jmhJar", "jmhReport") -> true

            // Spotless plugin
            task.name.startsWith("spotless") -> true

            // Gradle Doctor plugin
            task.name in listOf(
                "buildHealth",
                "projectHealth",
                "graph", "graphMain",
                "projectGraphReport",
                "ripples",
                "aggregateAdvice",
            ) -> true
            task.name.startsWith("abiAnalysis") -> true
            task.name.startsWith("advice") -> true
            task.name.startsWith("analyzeClassUsage") -> true
            task.name.startsWith("analyzeJar") -> true
            task.name.startsWith("artifactsReport") -> true
            task.name.startsWith("constantUsageDetector") -> true
            task.name.startsWith("createVariantFiles") -> true
            task.name.startsWith("findDeclaredProcs") -> true
            task.name.startsWith("findUnusedProcs") -> true
            task.name.startsWith("generalsUsageDetector") -> true
            task.name.startsWith("importFinder") -> true
            task.name.startsWith("inlineMemberExtractor") -> true
            task.name.startsWith("locateDependencies") -> true
            task.name.startsWith("misusedDependencies") -> true
            task.name.startsWith("reason") -> true
            task.name.startsWith("redundantKaptCheck") -> true
            task.name.startsWith("redundantPluginAlert") -> true
            task.name.startsWith("serviceLoader") -> true

            else -> false
        }
    }

    gradle.taskGraph.whenReady {
        val unsupportedTasks = allTasks.filter(unsupportedTasksPredicate)
        if (unsupportedTasks.isNotEmpty()) {
            val maxDisplayed = 5
            val displayedTasks = unsupportedTasks.takeLast(maxDisplayed).reversed().joinToString(separator = ", ") { it.path } +
                if (unsupportedTasks.size > maxDisplayed) " and more..."
                else ""
            throw GradleException("""
                Tasks unsupported with the configuration cache were scheduled: $displayedTasks

                  The gradle/gradle build enables the configuration cache as an experiment.
                  It seems you are using a feature of this build that is not yet supported.

                  You can disable the configuration cache with `--no-configuration-cache`.
                  You can ignore problems with `--configuration-cache-problems=warn`.

                  Please see further instructions in CONTRIBUTING.md
            """.trimIndent())
        }
    }
}
