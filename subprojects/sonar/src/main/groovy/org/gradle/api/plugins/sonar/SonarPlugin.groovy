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
import org.gradle.api.internal.Instantiator
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.util.GradleVersion
import org.gradle.util.Jvm
import org.gradle.api.plugins.sonar.model.*

/**
 * A {@link Plugin} for integrating with <a href="http://www.sonarsource.org">Sonar</a>, a web-based platform
 * for managing code quality. Adds a task named <tt>sonarAnalyze</tt> of type {@link SonarAnalyze} that performs the code
 * analysis. Further adds a model object named <tt>sonar</tt> of type {@type SonarRootModel} that holds all
 * configuration information. By default, all Java sources in the main source set will be analyzed.
 */
class SonarPlugin implements Plugin<ProjectInternal> {
    static final String SONAR_ANALYZE_TASK_NAME = "sonarAnalyze"

    private Instantiator instantiator

    void apply(ProjectInternal project) {
        instantiator = project.services.get(Instantiator)
        def task = configureSonarTask(project)
        def model = configureSonarRootModel(project)
        task.rootModel = model

        configureSubprojects(project, model)
    }

    private SonarAnalyze configureSonarTask(Project project) {
        project.tasks.add(SONAR_ANALYZE_TASK_NAME, SonarAnalyze)
    }

    private SonarRootModel configureSonarRootModel(Project project) {
        def model = instantiator.newInstance(SonarRootModel)
        project.extensions.sonar = model
        model.conventionMapping.with {
            bootstrapDir = { new File(project.buildDir, "sonar") }
            gradleVersion = { GradleVersion.current().version }
        }

        model.server = configureSonarServer()
        model.database = configureSonarDatabase()
        model.project = configureSonarProject(project)

        model
    }

    private SonarServer configureSonarServer() {
        def server = instantiator.newInstance(SonarServer)
        server.url = "http://localhost:9000"
        server
    }

    private SonarDatabase configureSonarDatabase() {
        def database = instantiator.newInstance(SonarDatabase)
        database.url = "jdbc:derby://localhost:1527/sonar"
        database.driverClassName = "org.apache.derby.jdbc.ClientDriver"
        database.username = "sonar"
        database.password = "sonar"
        database
    }

    private void configureSubprojects(Project parentProject, SonarModel parentModel) {
        for (childProject in parentProject.childProjects.values()) {
            def childModel = instantiator.newInstance(SonarProjectModel)
            parentModel.childModels << childModel

            childProject.extensions.sonar = childModel
            childModel.project = configureSonarProject(childProject)

            configureSubprojects(childProject, childModel)
        }
    }

    private SonarProject configureSonarProject(Project project) {
        def sonarProject = instantiator.newInstance(SonarProject)

        sonarProject.conventionMapping.with {
            key = { "$project.group:$project.name" as String }
            name = { project.name }
            description = { project.description }
            version = { project.version.toString() }
            baseDir = { project.projectDir }
            workDir = { new File(project.buildDir, "sonar") }
            dynamicAnalysis = { "false" }
        }

        def javaSettings = instantiator.newInstance(SonarJavaSettings)
        sonarProject.java = javaSettings

        project.plugins.withType(JavaBasePlugin) {
            javaSettings.conventionMapping.with {
                sourceCompatibility = { project.sourceCompatibility.toString() }
                targetCompatibility = { project.targetCompatibility.toString() }
            }
        }

        project.plugins.withType(JavaPlugin) {
            def main = project.sourceSets.main
            def test = project.sourceSets.test

            sonarProject.conventionMapping.with {
                sourceDirs = { main.allSource.srcDirs as List }
                testDirs = { test.allSource.srcDirs as List }
                binaryDirs = { [main.output.classesDir] }
                libraries = {
                    def libraries = main.compileClasspath
                    def runtimeJar = Jvm.current().runtimeJar
                    if (runtimeJar != null) {
                        libraries += project.files(runtimeJar)
                    }
                    libraries
                }
                dynamicAnalysis = { "reuseReports" }
                testReportPath = { project.test.testResultsDir }
                language = { "java" }
            }
        }

        sonarProject
    }
}
