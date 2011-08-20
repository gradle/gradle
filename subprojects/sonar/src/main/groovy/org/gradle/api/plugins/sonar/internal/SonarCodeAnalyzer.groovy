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
import org.sonar.api.batch.bootstrap.ProjectDefinition
import org.sonar.api.batch.bootstrap.ProjectReactor
import org.sonar.batch.Batch
import org.sonar.batch.bootstrapper.EnvironmentInformation
import org.gradle.api.plugins.sonar.model.SonarProject
import org.gradle.api.plugins.sonar.model.SonarModel
import org.gradle.api.plugins.sonar.model.ModelToPropertiesConverter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Runs Sonar code analysis for a project hierarchy.
 */
class SonarCodeAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SonarCodeAnalyzer)

    // global Sonar settings
    SonarModel sonarModel
    // root of the project hierarchy to be analyzed
    SonarProject sonarProject

    void execute() {
        def projectDef = configureProject(sonarProject)
        def reactor = new ProjectReactor(projectDef)
        def globalProperties = extractProperties(sonarModel)
        configureAdditionalGlobalProperties(globalProperties)
        for (prop in globalProperties) {
            LOGGER.info("adding global property $prop")
        }
        def environment = new EnvironmentInformation("Gradle", sonarModel.gradleVersion)
        def batch = Batch.create(reactor, new MapConfiguration(globalProperties), environment)
        batch.execute()
    }

    ProjectDefinition configureProject(SonarProject sonarProject) {
        LOGGER.info("configuring project $sonarProject.name")

        def projectProperties = new Properties()
        projectProperties.putAll(extractProperties(sonarProject))
        for (prop in projectProperties) {
            LOGGER.info("adding project property $prop")
        }

        def projectDef = ProjectDefinition.create(projectProperties)
        projectDef.key = sonarProject.key
        projectDef.name = sonarProject.name
        projectDef.description = sonarProject.description
        projectDef.version = sonarProject.version
        projectDef.baseDir = sonarProject.baseDir
        projectDef.workDir = sonarProject.workDir

        for (dir in sonarProject.sourceDirs) {
            LOGGER.info("adding source dir $dir")
        }
        projectDef.sourceDirs = sonarProject.sourceDirs as File[]

        for (dir in sonarProject.testDirs) {
            LOGGER.info("adding test dir $dir")
        }
        projectDef.testDirs = sonarProject.testDirs as File[]

        for (dir in sonarProject.binaryDirs) {
            LOGGER.info("adding binary dir $dir")
            projectDef.addBinaryDir(dir)
        }

        for (lib in sonarProject.libraries) {
            LOGGER.info("adding library $lib")
            projectDef.addLibrary(lib.absolutePath)
        }

        for (subproject in sonarProject.subprojects) {
            def subprojectDef = configureProject(subproject)
            projectDef.addSubProject(subprojectDef)
        }

        projectDef
    }

    private Map<String, String> extractProperties(model) {
        def converter = new ModelToPropertiesConverter(model)
        converter.propertyProcessors = model.propertyProcessors
        converter.convert()
    }

    private void configureAdditionalGlobalProperties(Map<String, String> globalProperties) {
        globalProperties.skippedModules = getSkippedProjects(sonarProject)*.key.join(",")
    }

    private List<SonarProject> getSkippedProjects(SonarProject sonarProject, List<SonarProject> skipped = []) {
        if (sonarProject.skip) {
            skipped << sonarProject
        }
        for (subproject in sonarProject.subprojects) {
            getSkippedProjects(subproject, skipped)
        }
        skipped
    }
}
