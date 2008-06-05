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
import org.gradle.api.Task

/**
 * @author Hans Dockter
 */
class Bundle extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(Resources)

    static final BASENAME_KEY = 'basename'
    static final CLASSIFIER_KEY = 'classifier'

    Bundle self

    String tasksBaseName

    Set childrenDependOn = []

    Map defaultArchiveTypes

    List archiveNames = []

    Bundle(Project project, String name) {
        super(project, name)
        self = this
    }

    AbstractArchiveTask createArchive(ArchiveType type, Closure configureClosure) {
        createArchive(type, [:], configureClosure)
    }

    AbstractArchiveTask createArchive(ArchiveType type, Map args = [:], Closure configureClosure = null) {
        String baseName = args.baseName ?: self.tasksBaseName
        String classifier = args.classifier ? '_' + args.classifier : ''
        String taskName = "$baseName${classifier}_$type.defaultExtension"
        AbstractArchiveTask archiveTask = project.createTask(taskName, type: type.taskClass)
        archiveTask.conventionMapping(type.conventionMapping)
        archiveTask.baseName = baseName
        archiveTask.classifier = classifier ? classifier.substring(1) : ''
        if (configureClosure) {archiveTask.configure(configureClosure)}
        setTaskDependsOn(archiveTask, childrenDependOn)
        this.dependsOn(taskName)
        archiveNames << archiveTask.name
        archiveTask
    }

    private void setTaskDependsOn(AbstractArchiveTask task, Set childrenDependOn) {
        if (childrenDependOn) {
            task.dependsOn = childrenDependOn
        } else {
            task.dependsOn = this.dependsOn - archiveNames
        }
    }

    Jar jar(Closure configureClosure = null) {
        jar([:], configureClosure)
    }

    Jar jar(Map args = [:], Closure configureClosure = null) {
        createArchive(self.defaultArchiveTypes[Jar.DEFAULT_EXTENSION], args, configureClosure)
    }

    War war(Closure configureClosure = null) {
        war([:], configureClosure)
    }

    War war(Map args = [:], Closure configureClosure = null) {
        createArchive(self.defaultArchiveTypes[War.WAR_EXTENSION], args, configureClosure)
    }

    Zip zip(Closure configureClosure) {
        zip([:], configureClosure)
    }

    Zip zip(Map args = [:], Closure configureClosure = null) {
        createArchive(self.defaultArchiveTypes[Zip.ZIP_EXTENSION], args, configureClosure)
    }

    Tar tar(Closure configureClosure = null) {
        tar([:], configureClosure)
    }

    Tar tar(Map args = [:], Closure configureClosure = null) {
        createArchive(self.defaultArchiveTypes[Tar.TAR_EXTENSION], args, configureClosure)
    }

    Tar tarGz(Closure configureClosure = null) {
        tarGz([:], configureClosure)
    }

    Tar tarGz(Map args = [:], Closure configureClosure = null) {
        Tar tar = createArchive(self.defaultArchiveTypes[Tar.TAR_EXTENSION + Compression.GZIP.extension], args, configureClosure)
        tar.compression = Compression.GZIP
        tar
    }

    Tar tarBzip2(Closure configureClosure = null) {
        tarBzip2([:], configureClosure)
    }

    Tar tarBzip2(Map args = [:], Closure configureClosure = null) {
        Tar tar = createArchive(self.defaultArchiveTypes[Tar.TAR_EXTENSION + Compression.BZIP2.extension], args, configureClosure)
        tar.compression = Compression.BZIP2
        tar
    }
}