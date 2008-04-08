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

import org.gradle.api.DependencyManager
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.util.FileSet
import org.gradle.util.GradleUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 */
abstract class AbstractArchiveTask extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(AbstractArchiveTask)

    File baseDir
    
    List resourceCollections = []

    boolean createIfEmpty = false
    
    File destinationDir

    String customName

    String baseName

    String version

    String extension

    boolean publish = true

    String[] configurations

    DependencyManager dependencyManager

    def customSelector

    private AbstractArchiveTask self

    AbstractArchiveTask(Project project, String name) {
        super(project, name)
        doLast(this.&generateArchive)
        self = this
    }

    AbstractArchiveTask publish(boolean publish) {
        this.publish = publish
        this
    }

    AbstractArchiveTask configurations(String[] configurations) {
        this.configurations = configurations
        this                       
    }

    void generateArchive(Task task) {
        logger.debug("Creating archive: $name")
        if (!self.destinationDir) {
            throw new InvalidUserDataException('You mustspecify the destinationDir.')
        }
        createAntArchiveTask().call()
        if (publish) {
            self.configurations.each {self.dependencyManager.addArtifacts(it, "${self.baseName}.$self.extension")}
        }
    }

    abstract Closure createAntArchiveTask()

    String getArchiveName() {
        if (customName) { return customName }
        self.baseName + (self.version ? "-$self.version" : "")  + ".$self.extension"
    }

    FileSet fileSet(Closure configureClosure) {
        fileSet([:], configureClosure)
    }

    FileSet fileSet(Map args = [:], Closure configureClosure = null) {
        createFileSetInternal(args, FileSet, configureClosure)
    }

    protected def createFileSetInternal(Map args, Class type, Closure configureClosure) {
        args.dir = args.dir ?: self.baseDir
        def fileSet = type.newInstance(args)
        resourceCollections << GradleUtil.configure(configureClosure, fileSet)
        fileSet
    }

    File getArchivePath() {
        new File(self.destinationDir, self.archiveName)
    }

}
