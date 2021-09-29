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
import org.gradle.api.internal.plugins.DslObject
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
            task.name in listOf(
                "publishLocalPublicationToLocalRepository",
                "validateExternalPlugins",
            ) -> false
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
            task.name.startsWithAnyOf(
                "publish",
                "idea",
            ) -> true
            task is GradleBuild -> true

            // gradle/gradle build tasks
            task.name in listOf(
                "updateInitPluginTemplateVersionFile",
                "buildshipEclipseProject",
                "resolveAllDependencies",
            ) -> true
            task.name.endsWith("Wrapper") -> true
            task.name in listOf("docs", "stageDocs", "docsTest", "serveDocs") -> true
            task.name.startsWith("userguide") -> true
            task.name.contains("Sample") -> true
            task.name.contains("Snippet") -> true
            task.typeSimpleName in listOf(
                "KtsProjectGeneratorTask",
                "JavaExecProjectGeneratorTask",
                "JvmProjectGeneratorTask",
                "NativeProjectGeneratorTask",
                "MonolithicNativeProjectGeneratorTask",
                "NativeProjectWithDepsGeneratorTask",
                "CppMultiProjectGeneratorTask",
                "BuildBuilderGenerator",
            ) -> true

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
            task.name.startsWithAnyOf(
                "abiAnalysis",
                "advice",
                "analyzeClassUsage",
                "analyzeJar",
                "artifactsReport",
                "constantUsageDetector",
                "createVariantFiles",
                "findDeclaredProcs",
                "findUnusedProcs",
                "generalsUsageDetector",
                "importFinder",
                "inlineMemberExtractor",
                "locateDependencies",
                "misusedDependencies",
                "reason",
                "redundantKaptCheck",
                "redundantPluginAlert",
                "serviceLoader",
            ) -> true

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


fun String.startsWithAnyOf(vararg prefixes: String): Boolean =
    prefixes.any { prefix -> startsWith(prefix) }

val Task.typeSimpleName: String
    get() = DslObject(this).declaredType.simpleName
