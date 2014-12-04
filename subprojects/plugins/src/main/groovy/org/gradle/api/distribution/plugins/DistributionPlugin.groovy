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

import org.gradle.api.*
import org.gradle.api.distribution.Distribution
import org.gradle.api.distribution.internal.DefaultDistributionContainer
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.internal.reflect.Instantiator

import javax.inject.Inject

/**
 * <p>A {@link Plugin} to package project as a distribution.</p>
 *
 *
 */
@Incubating
class DistributionPlugin implements Plugin<ProjectInternal> {
    /**
     * Name of the main distribution
     */
    private static final String MAIN_DISTRIBUTION_NAME = "main"

    private static final String DISTRIBUTION_GROUP = "distribution"
    private static final String TASK_DIST_ZIP_NAME = "distZip"
    private static final String TASK_DIST_TAR_NAME = "distTar"
    private static final String TASK_INSTALL_NAME = "installDist"

    private final Instantiator instantiator
    private final FileOperations fileOperations

    @Inject
    public DistributionPlugin(Instantiator instantiator, FileOperations fileOperations) {
        this.instantiator = instantiator;
        this.fileOperations = fileOperations
    }

    public void apply(ProjectInternal project) {
        project.pluginManager.apply(BasePlugin)

        def distributions = project.extensions.create("distributions", DefaultDistributionContainer, Distribution, instantiator, fileOperations)
        // TODO - refactor this action out so it can be unit tested
        distributions.all { dist ->
            dist.conventionMapping.map("baseName", { dist.name == MAIN_DISTRIBUTION_NAME ? project.name : String.format("%s-%s", project.name, dist.name) })
            dist.contents.from("src/${dist.name}/dist")
            def zipTask = addZipTask(project, dist)
            def tarTask = addTarTask(project, dist)
            addAssembleTask(project, dist, zipTask, tarTask)
            addInstallTask(project, dist)
        }
        distributions.create(MAIN_DISTRIBUTION_NAME)
    }

    Task addZipTask(Project project, Distribution distribution) {
        def taskName = TASK_DIST_ZIP_NAME
        if (!MAIN_DISTRIBUTION_NAME.equals(distribution.name)) {
            taskName = distribution.name + "DistZip"
        }
        configureArchiveTask(project, taskName, distribution, Zip)
    }

    Task addTarTask(Project project, Distribution distribution) {
        def taskName = TASK_DIST_TAR_NAME
        if (!MAIN_DISTRIBUTION_NAME.equals(distribution.name)) {
            taskName = distribution.name + "DistTar"
        }
        configureArchiveTask(project, taskName, distribution, Tar)
    }

    private <T extends AbstractArchiveTask> Task configureArchiveTask(Project project, String taskName, Distribution distribution, Class<T> type) {
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
        ArchivePublishArtifact archiveArtifact = new ArchivePublishArtifact(archiveTask);
        project.getExtensions().getByType(DefaultArtifactPublicationSet.class).addCandidate(archiveArtifact);
        archiveTask
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

    private void addAssembleTask(Project project, Distribution distribution, Task... tasks) {
        def taskName = "assemble" + distribution.name.capitalize() + "Dist"
        Task assembleTask = project.getTasks().create(taskName);
        assembleTask.setDescription("Assembles the " + distribution.name + " distributions");
        assembleTask.setGroup(DISTRIBUTION_GROUP);
        assembleTask.dependsOn(tasks);
    }
}