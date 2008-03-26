/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.bundling

import org.gradle.api.Project
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.Resources
import org.gradle.api.tasks.bundling.Jar
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 */
class Bundle extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(Resources)

    Bundle self

    String tasksBaseName

    Set childrenDependOn = []

    Map defaultArchiveTypes

    List bundleNames = []

    Bundle(Project project, String name) {
        super(project, name)
        self = this
    }

    AbstractArchiveTask createArchive(ArchiveType type, Closure configureClosure = null) {
        createArchive(type, null, configureClosure)
    }

    AbstractArchiveTask createArchive(ArchiveType type, String baseName, Closure configureClosure = null) {
        String taskName = (baseName ?: self.tasksBaseName) + "_$type.defaultExtension"
        AbstractArchiveTask archiveTask = project.createTask(taskName, type: type.taskClass)
        archiveTask.convention(convention, type.conventionMapping)
        if (configureClosure) {archiveTask.configure(configureClosure)}
        archiveTask.baseName = (baseName ?: self.tasksBaseName)
        archiveTask.dependsOn = self.childrenDependOn
        this.dependsOn(taskName)
        bundleNames << archiveTask.name
        archiveTask
    }

    Jar jar(String baseName = null, Closure configureClosure = null) {
        createArchive(self.defaultArchiveTypes[Jar.DEFAULT_EXTENSION], baseName, configureClosure)
    }

    War war(String baseName = null, Closure configureClosure = null) {
        createArchive(self.defaultArchiveTypes[War.WAR_EXTENSION], baseName, configureClosure)
    }

    Zip zip(Closure configureClosure) {
        zip(null, configureClosure)
    }

    Zip zip(String baseName = null, Closure configureClosure = null) {
        createArchive(self.defaultArchiveTypes[Zip.ZIP_EXTENSION], baseName, configureClosure)
    }

    Tar tar(String baseName = null, Closure configureClosure = null) {
        createArchive(self.defaultArchiveTypes[Tar.TAR_EXTENSION], baseName, configureClosure)
    }

    Tar tarGz(String baseName = null, Closure configureClosure = null) {
        Tar tar = createArchive(self.defaultArchiveTypes[Tar.TAR_EXTENSION + Compression.GZIP.extension], baseName, configureClosure)
        tar.compression = Compression.GZIP
        tar
    }

    Tar tarBzip2(String baseName = null, Closure configureClosure = null) {
        Tar tar = createArchive(self.defaultArchiveTypes[Tar.TAR_EXTENSION + Compression.BZIP2.extension], baseName, configureClosure)
        tar.compression = Compression.BZIP2
        tar
    }
}