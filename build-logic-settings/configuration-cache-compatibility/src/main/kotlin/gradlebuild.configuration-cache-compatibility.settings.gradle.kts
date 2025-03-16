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
import org.gradle.api.internal.plugins.DslObject

run {

    fun String.startsWithAnyOf(vararg prefixes: String): Boolean =
        prefixes.any { prefix -> startsWith(prefix) }

    fun Task.typeSimpleName(): String =
        DslObject(this).declaredType.simpleName

    fun isIncompatible(task: Task): Boolean = when {

        // Working tasks that would otherwise be matched by filters below
        task.name in listOf(
            "publishLocalPublicationToLocalRepository",
            "publishEmbeddedKotlinPluginMarkerMavenPublicationToTestRepository",
            "publishKotlinDslBasePluginMarkerMavenPublicationToTestRepository",
            "publishKotlinDslCompilerSettingsPluginMarkerMavenPublicationToTestRepository",
            "publishKotlinDslPluginMarkerMavenPublicationToTestRepository",
            "publishKotlinDslPrecompiledScriptPluginsPluginMarkerMavenPublicationToTestRepository",
            "publishPluginMavenPublicationToTestRepository",
            "publishPluginsToTestRepository",
        ) -> false

        // Core tasks
        task.name in listOf(
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
            "resolveAllDependencies",
        ) -> true

        task.name.endsWith("Wrapper") -> true
        task.name in listOf("docs", "stageDocs", "serveDocs") -> true
        task.name.startsWith("userguide") -> true
        task.name == "samplesMultiPage" -> true
        task.typeSimpleName() in listOf(
            "KtsProjectGeneratorTask",
            "JavaExecProjectGeneratorTask",
            "JvmProjectGeneratorTask",
            "NativeProjectGeneratorTask",
            "MonolithicNativeProjectGeneratorTask",
            "NativeProjectWithDepsGeneratorTask",
            "CppMultiProjectGeneratorTask",
            "BuildBuilderGenerator",
            "PerformanceTest",
            "BuildCommitDistribution",
            "DetermineBaselines",
        ) -> true

        // Third parties tasks

        // Publish plugin
        task.name == "login" -> true

        // Kotlin/JS
        // https://youtrack.jetbrains.com/issue/KT-50881
        task.name in listOf("generateExternals") -> true

        // JMH plugin
        task.name in listOf("jmh", "jmhJar", "jmhReport") -> true

        // Gradle Doctor plugin
        task.name in listOf(
            "graph", "graphMain",
            "projectGraphReport",
            "ripples",
            "aggregateAdvice",
        ) -> true

        task.name.startsWithAnyOf(
            "advice",
            "analyzeClassUsage",
            "analyzeJar",
            "constantUsageDetector",
            "createVariantFiles",
            "findUnusedProcs",
            "generalsUsageDetector",
            "importFinder",
            "inlineMemberExtractor",
            "locateDependencies",
            "misusedDependencies",
            "reason",
            "redundantKaptCheck",
            "redundantPluginAlert",
        ) -> true

        else -> false
    }

    gradle.lifecycle.beforeProject {
        tasks.configureEach {
            val task = this
            if (isIncompatible(task)) {
                task.notCompatibleWithConfigurationCache("Task is not compatible with the configuration cache")
            }
        }
    }
}
