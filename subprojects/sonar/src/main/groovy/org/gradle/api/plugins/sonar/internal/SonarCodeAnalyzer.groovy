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
import org.gradle.api.plugins.sonar.model.ModelToPropertiesConverter
import org.gradle.api.plugins.sonar.model.SonarRootModel
import org.gradle.api.plugins.sonar.model.SonarModel
import org.sonar.api.batch.bootstrap.ProjectDefinition
import org.sonar.api.batch.bootstrap.ProjectReactor
import org.sonar.batch.Batch
import org.sonar.batch.bootstrapper.EnvironmentInformation
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Runs Sonar code analysis for a project hierarchy.
 * This class lives on the Sonar bootstrapper's class loader.
 */
class SonarCodeAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SonarCodeAnalyzer)

    SonarRootModel rootModel

    void execute() {
        if (skipped(rootModel)) {
            return
        }

        def projectDef = configureProject(rootModel)
        def reactor = new ProjectReactor(projectDef)
        def globalProperties = extractProperties(rootModel)
        for (prop in globalProperties) {
            LOGGER.info("adding global property $prop")
        }
        def environment = new EnvironmentInformation("Gradle", rootModel.gradleVersion)
        def batch = Batch.create(reactor, new MapConfiguration(globalProperties), environment)
        batch.execute()
    }

    ProjectDefinition configureProject(SonarModel sonarModel) {
        def sonarProject = sonarModel.project

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

        for (childModel in sonarModel.childModels) {
            if (skipped(childModel)) {
                continue
            }
            def childProjectDef = configureProject(childModel)
            projectDef.addSubProject(childProjectDef)
        }

        projectDef
    }

    private boolean skipped(SonarModel model) {
        if (model.project.skip) {
            LOGGER.info("Skipping Sonar analysis for project '{}' and its subprojects because 'sonar.project.skip' is 'true'", model.project.name)
            return true
        }
        false
    }

    private Map<String, String> extractProperties(model) {
        def converter = new ModelToPropertiesConverter(model)
        converter.propertyProcessors = model.propertyProcessors
        converter.convert()
    }
}
