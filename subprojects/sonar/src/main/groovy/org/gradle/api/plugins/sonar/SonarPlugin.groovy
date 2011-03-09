/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.sonar

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin

/**
 * A {@link Plugin} for integrating with <a href="http://www.sonarsource.org">Sonar</a>, a web-based platform
 * for managing code quality. Adds a task named <tt>sonar</tt> with type {@link Sonar} and configures it to
 * analyze the Java sources in the main source set.
 */
class SonarPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.plugins.withType(JavaPlugin) {
            def main = project.sourceSets.main
            def test = project.sourceSets.test
            def sonarTask = project.tasks.add("sonar", Sonar)
            sonarTask.conventionMapping.serverUrl = { "http://localhost:9000" }
            sonarTask.conventionMapping.projectDir = { project.projectDir }
            sonarTask.conventionMapping.projectMainSourceDirs = { main.java.srcDirs }
            sonarTask.conventionMapping.projectTestSourceDirs = { test.java.srcDirs }
            sonarTask.conventionMapping.projectClassesDirs = { [main.classesDir] as Set }
            sonarTask.conventionMapping.projectDependencies = {
                def files = project.configurations.compile.resolve()
                files.findAll { it.name.endsWith(".jar") }.collect { it.path } as Set
            }
            sonarTask.conventionMapping.projectKey = { "$project.group:$project.name" as String }
            sonarTask.conventionMapping.projectName = { project.name }
            sonarTask.conventionMapping.projectDescription = { project.description }
            sonarTask.conventionMapping.projectVersion = { project.version as String }
            sonarTask.conventionMapping.projectProperties = {
                ["sonar.java.source": project.sourceCompatibility as String,
                 "sonar.java.target": project.targetCompatibility as String,
                 "sonar.dynamicAnalysis": "reuseReports",
                 "sonar.surefire.reportsPath": project.test.testResultsDir as String]
            }
        }
    }
}
