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
import org.gradle.api.tasks.SourceSet
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.cache.CacheRepository

/**
 * A {@link Plugin} for integrating with <a href="http://www.sonarsource.org">Sonar</a>, a web-based platform
 * for managing code quality. Adds a task named <tt>sonar</tt> with type {@link Sonar} and configures it to
 * analyze the Java sources in the main source set.
 */
class SonarPlugin implements Plugin<Project> {
    static final String SONAR_TASK_NAME = "sonar"

    void apply(Project project) {
        project.plugins.withType(JavaPlugin) {
            def sonarTask = project.tasks.add(SONAR_TASK_NAME, Sonar)
            configureConventions(sonarTask, project)
        }
    }

    private void configureConventions(Sonar sonarTask, Project project) {
        def main = project.sourceSets.main
        def test = project.sourceSets.test

        sonarTask.conventionMapping.serverUrl = { "http://localhost:9000" }
        sonarTask.conventionMapping.bootstrapDir = {
            def cacheRepository = (project as ProjectInternal).services.get(CacheRepository)
            cacheRepository.cache("sonar-bootstrap").forObject(project.gradle).open().baseDir
        }
        sonarTask.conventionMapping.projectDir = { project.projectDir }
        sonarTask.conventionMapping.buildDir = { project.buildDir }
        sonarTask.conventionMapping.projectMainSourceDirs = { getSourceDirs(main) }
        sonarTask.conventionMapping.projectTestSourceDirs = { getSourceDirs(test) }
        sonarTask.conventionMapping.projectClassesDirs = { [main.classesDir] as Set }
        sonarTask.conventionMapping.projectDependencies = { project.configurations.compile.resolve() }
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

    private Set<File> getSourceDirs(SourceSet sourceSet) {
        sourceSet.allSource.sourceTrees.srcDirs.flatten() as LinkedHashSet
    }
}
