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
package org.gradle.api.plugins.sonar.internal

import org.apache.commons.configuration.MapConfiguration
import org.sonar.api.CoreProperties
import org.sonar.batch.Batch
import org.sonar.batch.bootstrapper.EnvironmentInformation
import org.sonar.batch.bootstrapper.ProjectDefinition
import org.sonar.batch.bootstrapper.Reactor
import org.gradle.util.GradleVersion

/**
 * Runs Sonar code analysis using the configuration of the Sonar task.
 */
class SonarCodeAnalyzer {
    def sonarTask

    void execute() {
        def globalProperties = new MapConfiguration(sonarTask.globalProperties)

        def projectProperties = new Properties()
        properties.putAll(sonarTask.projectProperties)
        projectProperties[CoreProperties.PROJECT_KEY_PROPERTY] = sonarTask.projectKey
        projectProperties[CoreProperties.PROJECT_VERSION_PROPERTY] = sonarTask.projectVersion

        def project = new ProjectDefinition(sonarTask.projectDir, sonarTask.bootstrapDir, projectProperties)
        sonarTask.projectMainSourceDirs.each { project.addSourceDir(it.path) }
        sonarTask.projectTestSourceDirs.each { project.addTestDir(it.path) }
        sonarTask.projectClassesDirs.each { project.addBinaryDir(it.path) }
        sonarTask.projectDependencies.each { project.addLibrary(it.path) }

        def reactor = new Reactor(project)
        def environment = new EnvironmentInformation("Gradle", GradleVersion.current().version)
        def batch = new Batch(globalProperties, project, reactor, environment)
        batch.execute()
    }
}
