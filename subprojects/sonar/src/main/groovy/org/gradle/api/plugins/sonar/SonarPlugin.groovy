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
import org.gradle.api.internal.ClassGenerator
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.Project
import org.gradle.util.GradleVersion
import org.gradle.api.plugins.sonar.model.SonarJavaSettings
import org.gradle.api.plugins.sonar.model.SonarProject
import org.gradle.api.plugins.sonar.model.SonarDatabase
import org.gradle.api.plugins.sonar.model.SonarServer
import org.gradle.api.plugins.sonar.model.SonarModel
import org.gradle.util.Jvm

/**
 * A {@link Plugin} for integrating with <a href="http://www.sonarsource.org">Sonar</a>, a web-based platform
 * for managing code quality. Adds a task named <tt>sonar</tt> with type {@link SonarTask} and configures it to
 * analyze the Java sources in the main source set.
 */
class SonarPlugin implements Plugin<ProjectInternal> {
    static final String SONAR_TASK_NAME = "sonar"

    private ProjectInternal project
    private ClassGenerator classGenerator

    void apply(ProjectInternal project) {
        this.project = project
        classGenerator = project.services.get(ClassGenerator)

        configureSonarTask()
    }

    private void configureSonarTask() {
        def sonarTask = project.tasks.add(SONAR_TASK_NAME, SonarTask)
        sonarTask.sonarModel = configureSonarModel()
        sonarTask.sonarProject = configureSonarProject(project)
    }

    private SonarModel configureSonarModel() {
        def sonarModel = classGenerator.newInstance(SonarModel)
        project.extensions.add("sonar", sonarModel)

        sonarModel.conventionMapping.with {
            bootstrapDir = { new File(project.buildDir, "sonar") }
            gradleVersion = { GradleVersion.current().version }
        }
        sonarModel.server = configureSonarServer()
        sonarModel.database = configureSonarDatabase()

        sonarModel
    }

    private SonarServer configureSonarServer() {
        def sonarServer = classGenerator.newInstance(SonarServer)
        sonarServer.url = "http://localhost:9000"
        sonarServer
    }

    private SonarDatabase configureSonarDatabase() {
        def sonarDatabase = classGenerator.newInstance(SonarDatabase)
        sonarDatabase.url = "jdbc:derby://localhost:1527/sonar"
        sonarDatabase.driverClassName = "org.apache.derby.jdbc.ClientDriver"
        sonarDatabase.username = "sonar"
        sonarDatabase.password = "sonar"
        sonarDatabase
    }

    private SonarProject configureSonarProject(Project project) {
        def sonarProject = classGenerator.newInstance(SonarProject)
        project.extensions.add("sonarProject", sonarProject)

        sonarProject.conventionMapping.with {
            key = { "$project.group:$project.name" as String }
            name = { project.name }
            description = { project.description }
            version = { project.version.toString() }
            baseDir = { project.projectDir }
            workDir = { new File(project.buildDir, "sonar") }
            dynamicAnalysis = { "false" }
        }

        def sonarJavaSettings = classGenerator.newInstance(SonarJavaSettings)
        sonarProject.java = sonarJavaSettings

        sonarProject.subprojects = project.childProjects.values().collect {
            configureSonarProject(it)
        }

        project.plugins.withType(JavaBasePlugin) {
            sonarJavaSettings.conventionMapping.with {
                sourceCompatibility = { project.sourceCompatibility.toString() }
                targetCompatibility = { project.targetCompatibility.toString() }
            }
        }

        project.plugins.withType(JavaPlugin) {
            def main = project.sourceSets.main
            def test = project.sourceSets.test

            sonarProject.conventionMapping.with {
                sourceDirs = { getSourceDirs(main) }
                testDirs = { getSourceDirs(test) }
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

    private List<File> getSourceDirs(SourceSet sourceSet) {
        sourceSet.allSource.srcDirs as List
    }
}
