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

package gradlebuild;

import org.gradle.api.Plugin;
import org.gradle.api.Task;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.tasks.GradleBuild;

import java.util.Arrays;

public class ConfigurationCacheCompatibilityPlugin implements Plugin<Settings> {

    @Override
    public void apply(Settings settings) {
        settings.getGradle().getLifecycle().beforeProject(project ->
            project.getTasks().configureEach(task -> {
                if (isIncompatible(task)) {
                    task.notCompatibleWithConfigurationCache("Task is not compatible with the configuration cache");
                }
            }));
    }

    private static boolean isIncompatible(Task task) {
        String name = task.getName();

        // Working tasks that would otherwise be matched by filters below
        if (Arrays.asList(
            "publishEmbeddedKotlinPluginMarkerMavenPublicationToTestRepository",
            "publishKotlinDslBasePluginMarkerMavenPublicationToTestRepository",
            "publishKotlinDslCompilerSettingsPluginMarkerMavenPublicationToTestRepository",
            "publishKotlinDslPluginMarkerMavenPublicationToTestRepository",
            "publishKotlinDslPrecompiledScriptPluginsPluginMarkerMavenPublicationToTestRepository",
            "publishLocalPublicationToLocalRepository",
            "publishPluginMavenPublicationToTestRepository",
            "publishPlugins",
            "publishPluginsToTestRepository"
        ).contains(name)) {
            return false;
        }

        // Core tasks
        if (Arrays.asList("components", "dependantComponents", "model").contains(name)) {
            return true;
        }
        if (startsWithAnyOf(name, "publish", "idea")) {
            return true;
        }
        if (task instanceof GradleBuild) {
            return true;
        }

        // gradle/gradle build tasks
        if (Arrays.asList("updateInitPluginTemplateVersionFile", "resolveAllDependencies").contains(name)) {
            return true;
        }
        if (name.endsWith("Wrapper")) {
            return true;
        }
        if (Arrays.asList("docs", "stageDocs", "serveDocs").contains(name)) {
            return true;
        }
        if (name.startsWith("userguide")) {
            return true;
        }
        if (name.equals("samplesMultiPage")) {
            return true;
        }
        if (Arrays.asList(
            "JavaExecProjectGeneratorTask",
            "NativeProjectWithDepsGeneratorTask",
            "PerformanceTest",
            "DetermineBaselines"
        ).contains(typeSimpleName(task))) {
            return true;
        }

        // Third parties tasks

        // Publish plugin
        if (name.equals("login")) {
            return true;
        }

        // Kotlin/JS
        // https://youtrack.jetbrains.com/issue/KT-50881
        if (name.equals("generateExternals")) {
            return true;
        }

        // JMH plugin
        if (Arrays.asList("jmh", "jmhJar", "jmhReport").contains(name)) {
            return true;
        }

        // Gradle Doctor plugin
        if (Arrays.asList("graph", "graphMain", "projectGraphReport", "ripples", "aggregateAdvice").contains(name)) {
            return true;
        }
        if (startsWithAnyOf(name,
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
            "redundantPluginAlert"
        )) {
            return true;
        }

        return false;
    }

    private static boolean startsWithAnyOf(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String typeSimpleName(Task task) {
        return new DslObject(task).getDeclaredType().getSimpleName();
    }
}
