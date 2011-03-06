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
import org.gradle.api.tasks.SourceSet
import org.gradle.api.plugins.JavaPlugin

/**
 * A {@link Plugin} for integration with Sonar. Adds a task named "sonar" and
 * configures it to analyze all Java sources in the main source set.
 */
class SonarPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.plugins.withType(JavaPlugin) {
            def sourceSet = project.sourceSets.main
            def sonarTask = project.tasks.add("sonar", Sonar)
            sonarTask.conventionMapping.serverUrl = { "http://localhost:9000" }
            sonarTask.conventionMapping.projectDir = { project.projectDir }
            sonarTask.conventionMapping.projectSourceDirs = { getJavaSourceDirs(sourceSet) }
            sonarTask.conventionMapping.projectClassesDir = { sourceSet.classesDir }
            sonarTask.conventionMapping.projectKey = { "$project.group:$project.name" as String }
            sonarTask.conventionMapping.projectName = { project.name }
            sonarTask.conventionMapping.projectDescription = { project.description }
            sonarTask.conventionMapping.projectVersion = { project.version }
        }
    }

    Set getJavaSourceDirs(SourceSet sourceSet) {
        sourceSet.java.srcDirs
    }
}
