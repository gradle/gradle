/*
 * Copyright 2022 the original author or authors.
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

package gradlebuild.plugins;

import gradlebuild.AbstractScannerPlugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.tasks.TaskProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PluginScannerPlugin extends AbstractScannerPlugin {

    List<String> toAnalyze = List.of(
        "org.springframework.boot", "org.jetbrains.kotlin.plugin.scripting", "org.jetbrains.kotlin.android.extensions",
        "org.jetbrains.kotlin.android", "org.jetbrains.kotlin.kapt", "org.jetbrains.kotlin.jvm",
        "org.jetbrains.kotlin.multiplatform.pm20", "org.jetbrains.kotlin.plugin.parcelize",
        "org.jetbrains.kotlin.native.cocoapods", "org.jetbrains.kotlin.js", "org.jetbrains.kotlin.multiplatform",
//        "com.gradle.build-scan",
        "com.github.johnrengelman.shadow", "org.sonarqube", "io.spring.dependency-management",
        "com.github.spotbugs", "org.jetbrains.kotlin.plugin.spring", "org.jetbrains.kotlin.plugin.allopen",
        "com.diffplug.gradle.spotless", "de.undercouch.download", "com.diffplug.spotless", "org.flywaydb.flyway",
//        "com.gradle.enterprise",
        "com.github.spotbugs-base", "io.freefair.lombok", "com.jfrog.artifactory",
        "org.gradle.kotlin.kotlin-dsl.compiler-settings", "org.gradle.kotlin.kotlin-dsl.precompiled-script-plugins",
        "org.gradle.kotlin.kotlin-dsl.base", "org.gradle.kotlin.kotlin-dsl", "org.gradle.kotlin.embedded-kotlin",
        "com.github.ben-manes.versions", "com.gorylenko.gradle-git-properties", "com.github.johnrengelman.plugin-shadow",
        "com.google.cloud.tools.jib", "org.jlleitschuh.gradle.ktlint-idea", "org.jlleitschuh.gradle.ktlint",
        "org.jetbrains.kotlin.plugin.serialization", "org.owasp.dependencycheck", "io.gitlab.arturbosch.detekt",
        "org.jetbrains.kotlin.plugin.noarg", "org.jetbrains.kotlin.plugin.jpa", "com.google.protobuf",
        "com.github.node-gradle.node", "com.github.node-gradle.grunt", "com.github.node-gradle.gulp",
        "com.adarshr.test-logger", "com.bmuschko.docker-remote-api", "com.bmuschko.docker-java-application",
        "com.bmuschko.docker-spring-boot-application", "org.openapi.generator"
    );

    @Override
    public void apply(Project project) {
        super.apply(project);

        project.getRepositories().gradlePluginPortal();

        List<TaskProvider<?>> analysisTasks = new ArrayList<>();
        for (String plugin : toAnalyze) {
            analysisTasks.add(analyzePlugin(plugin, project));
        }

        TaskProvider<PluginAnalysisAggregatorTask> analyzeAll = project.getTasks().register("analyzeAllPlugins", PluginAnalysisAggregatorTask.class, task -> {
            analysisTasks.forEach(analysis -> task.getAnalysisJson().from(analysis));
        });
    }

    private TaskProvider<?> analyzePlugin(String name, Project project) {
        Configuration dependencies = project.getConfigurations().create(name + "Dependencies");
        dependencies.setCanBeConsumed(false);
        dependencies.setCanBeResolved(false);
        dependencies.setVisible(true);

        dependencies.getDependencies().add(project.getDependencies().create(
            name + ":" + name + ".gradle.plugin" + ":+"
        ));

        Configuration configuration = project.getConfigurations().create(name + "Analysis");
        configuration.setCanBeResolved(true);
        configuration.setCanBeConsumed(false);
        configuration.setVisible(false);
        configuration.extendsFrom(dependencies);

        configureAsAnalysisClasspath(configuration);

        TaskProvider<GradleApiUsageCollectorTask> rawJson = project.getTasks().register(name + "AnalysisJson", GradleApiUsageCollectorTask.class, task -> {

            task.getPluginName().set(name);

            ArtifactCollection artifacts = configuration.getIncoming().getArtifacts();
            task.getAnalyzedClasspath().from(artifacts.getArtifactFiles());
            task.getArtifactIdentifiers().set(
                artifacts.getResolvedArtifacts().map(result ->
                    result.stream().map(ResolvedArtifactResult::getId).collect(Collectors.toList())
                )
            );
        });

        return rawJson;
    }
}
