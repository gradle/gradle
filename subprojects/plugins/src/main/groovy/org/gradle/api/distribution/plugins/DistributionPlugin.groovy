/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.distribution.plugins

import org.gradle.api.GradleException
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.distribution.Distribution
import org.gradle.api.distribution.internal.DefaultDistributionContainer
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.internal.reflect.Instantiator

import javax.inject.Inject

/**
 * Adds the ability to create distributions of the project.
 */
@Incubating
class DistributionPlugin implements Plugin<Project> {

    private static final String MAIN_DISTRIBUTION_NAME = "main"
    private static final String DISTRIBUTION_GROUP = "distribution"
    private static final String TASK_DIST_ZIP_NAME = "distZip"
    private static final String TASK_DIST_TAR_NAME = "distTar"
    private static final String TASK_INSTALL_NAME = "installDist"

    private final Instantiator instantiator
    private final FileOperations fileOperations

    @Inject
    DistributionPlugin(Instantiator instantiator, FileOperations fileOperations) {
        this.fileOperations = fileOperations
        this.instantiator = instantiator
    }

    void apply(Project project) {
        project.plugins.apply(BasePlugin)

        def distributions = project.extensions.create("distributions", DefaultDistributionContainer, Distribution, instantiator, fileOperations)

        // TODO - refactor this action out so it can be unit tested
        distributions.all { dist ->
            dist.baseName = dist.name == MAIN_DISTRIBUTION_NAME ? project.name : String.format("%s-%s", project.name, dist.name)
            dist.contents.from("src/$dist.name/dist")

            addZipTask(project, dist)
            addTarTask(project, dist)
            addInstallTask(project, dist)
        }

        distributions.create(MAIN_DISTRIBUTION_NAME)
    }

    void addZipTask(Project project, Distribution distribution) {
        def taskName = TASK_DIST_ZIP_NAME
        if (MAIN_DISTRIBUTION_NAME != distribution.name) {
            taskName = distribution.name + "DistZip"
        }
        configureArchiveTask(project, taskName, distribution, Zip)
    }

    void addTarTask(Project project, Distribution distribution) {
        def taskName = TASK_DIST_TAR_NAME
        if (MAIN_DISTRIBUTION_NAME != distribution.name) {
            taskName = distribution.name + "DistTar"
        }
        configureArchiveTask(project, taskName, distribution, Tar)
    }

    private <T extends AbstractArchiveTask> void configureArchiveTask(Project project, String taskName, Distribution distribution, Class<T> type) {
        def archiveTask = project.tasks.create(taskName, type)
        archiveTask.description = "Bundles the project as a distribution."
        archiveTask.group = DISTRIBUTION_GROUP
        archiveTask.conventionMapping.baseName = {
            if (distribution.baseName == null || distribution.baseName.equals("")) {
                throw new GradleException("Distribution baseName must not be null or empty! Check your configuration of the distribution plugin.")
            }
            distribution.baseName
        }
        def baseDir = { archiveTask.archiveName - ".${archiveTask.extension}" }
        archiveTask.into(baseDir) {
            with(distribution.contents)
        }
    }

    private void addInstallTask(Project project, Distribution distribution) {
        def taskName = TASK_INSTALL_NAME
        if (MAIN_DISTRIBUTION_NAME != distribution.name) {
            taskName = "install" + distribution.name.capitalize() + "Dist"
        }
        def installTask = project.tasks.create(taskName, Sync)
        installTask.description = "Installs the project as a distribution as-is."
        installTask.group = DISTRIBUTION_GROUP
        installTask.with distribution.contents
        installTask.into { project.file("${project.buildDir}/install/${distribution.baseName}") }
    }
}