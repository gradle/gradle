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
import org.gradle.api.tasks.util.FileCollection

/**
 * @author Hans Dockter
 */
abstract class AbstractArchiveTask extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(AbstractArchiveTask)

    /**
     * If you create a fileset and don't assign a directory to this fileset, the baseDir value is assigned to the dir
     * property of the fileset.
     */
    File baseDir

    /**
     * A list with all entities (e.g. filesets) which describe the files of this archive.
     */
    List resourceCollections = []

    /**
     * Controls if an archive gets created if no files would go into it.  
     */
    boolean createIfEmpty = false

    /**
     * The dir where the created archive is placed.
     */
    File destinationDir

    /**
     * Usually the archive name is composed out of the baseName, the version and the extension. If the custom name is set,
     * solely the customName is used as the archiveName.
     */
    String customName

    /**
     * The baseName of the archive.
     */
    String baseName

    /**
     * The version part of the archive name
     */
    String version

    /**
     * The extension part of the archive name
     */
    String extension

    /**
     * The classifier part of the archive name. Default to an empty string. Could be 'src'. 
     */
    String classifier = ''

    /**
     * Controlls whether the archive adds itself to the dependency configurations. Defaults to true.
     */
    boolean publish = true

    /**
     * The dependency configurations the archive gets added to if publish is true.
     */
    String[] configurations

    /**
     * The dependency manager to use for adding the archive to the configurations.
     */
    DependencyManager dependencyManager

    /**
     *
     */
    List mergeFileSets = []

    /**
     *
     */
    List mergeGroupFileSets = []

    protected ArchiveDetector archiveDetector = new ArchiveDetector()

    private AbstractArchiveTask self

    AbstractArchiveTask(Project project, String name) {
        super(project, name)
        doLast(this.&generateArchive)
        self = this
    }

    /**
     * Sets the publish property
     *
     * @param publish the value assigned to the publish property
     * @return this
     */
    AbstractArchiveTask publish(boolean publish) {
        this.publish = publish
        this
    }

    /**
     * Sets (not add) the configurations the archive gets published to.
     *
     * @param publish the value assigned to the publish property
     * @return this
     */
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
            String classifierSnippet = classifier ? ':' + classifier : ''
            self.configurations.each {self.dependencyManager.addArtifacts(it, "${self.baseName}${classifierSnippet}@$self.extension")}
        }
    }

    abstract Closure createAntArchiveTask()

    /**
     * Returns the archive name. If the customName is not set, the pattern for the name is:
     * [baseName]-[version].[extension]
     */
    String getArchiveName() {
        if (customName) { return customName }
        self.baseName + (self.version ? "-$self.version" : "") + (self.classifier ? "-$self.classifier" : "") +
                ".$self.extension"
    }

    /**
     * The path where the archive is constructed. The path is simply the destinationDir plus the archiveName.
     * @return a File object with the path to the archive
     */
    File getArchivePath() {
        new File(self.destinationDir, self.archiveName)
    }

    /**
     * Adds a fileset.
     * @param configureClosure configuration instructions
     * @return the added fileset
     */
    FileSet fileSet(Closure configureClosure) {
        fileSet([:], configureClosure)
    }

    /**
     * Add a fileset
     * @param args constructor arguments for the FileSet to construct
     * @param configureClosure configuration instructions
     * @return the added fileset
     */
    FileSet fileSet(Map args = [:], Closure configureClosure = null) {
        createFileSetInternal(args, FileSet, configureClosure)
    }

    protected def createFileSetInternal(Map args, Class type, Closure configureClosure) {
        args.dir = args.dir ?: self.baseDir
        def fileSet = type.newInstance(args)
        resourceCollections << GradleUtil.configure(configureClosure, fileSet)
        fileSet
    }

    /**
     * An arbitrary collection of files to the archive. In contrast to a fileset they don't need to have a common
     * basedir.
     */
    FileCollection files(File[] files) {
        FileCollection fileCollection = new FileCollection(files as Set)
        resourceCollections << fileCollection
        fileCollection
    }

    /**
     *
     */
    AbstractArchiveTask merge(Object[] archiveFiles) {
        Object[] flattenedArchiveFiles = archiveFiles
        Closure configureClosure = GradleUtil.extractClosure(flattenedArchiveFiles)
        if (configureClosure) {
            flattenedArchiveFiles = flattenedArchiveFiles[0..archiveFiles.length - 2]
            if (flattenedArchiveFiles.length == 1 && flattenedArchiveFiles instanceof Object[]) {
                flattenedArchiveFiles = flattenedArchiveFiles[0].collect {it}
            }
        }
        GradleUtil.fileList(flattenedArchiveFiles).collect { project.file(it) }.each {
            Class fileSetType = archiveDetector.archiveFileSetType(it)
            if (!fileSetType) { throw new InvalidUserDataException("File $it is not a valid archive or has no valid extension.") }
            def fileSet = fileSetType.newInstance(it)
            GradleUtil.configure(configureClosure, fileSet)
            mergeFileSets.add(fileSet)
        }
        this
    }

    /**
     * Defines a fileset of zip-like archives
     */
    AbstractArchiveTask mergeGroup(def dir, Closure configureClosure = null) {
        if (!dir) { throw new InvalidUserDataException('Dir argument must not be null!') }
        FileSet fileSet = new FileSet(dir as File)
        GradleUtil.configure(configureClosure, fileSet)
        mergeGroupFileSets.add(fileSet)
        this
    }
}
