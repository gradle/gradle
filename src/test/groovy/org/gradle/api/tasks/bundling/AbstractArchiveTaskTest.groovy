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

import groovy.mock.interceptor.MockFor
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
import org.gradle.api.tasks.AbstractConventionTaskTest
import org.gradle.api.tasks.util.FileSet
import org.gradle.util.HelperUtil
import org.gradle.api.tasks.util.FileCollection

/**
 * @author Hans Dockter
 */
abstract class AbstractArchiveTaskTest extends AbstractConventionTaskTest {
    abstract AbstractArchiveTask getArchiveTask()

    Task getTask() {
        archiveTask
    }

    abstract MockFor getAntMocker(boolean toBeCalled)

    abstract def getAnt()

    void setUp() {
        super.setUp()
    }

    void checkConstructor() {
        assertFalse(archiveTask.createIfEmpty)
        assertEquals([], archiveTask.resourceCollections)
    }

    protected void configure(AbstractArchiveTask archiveTask) {
        File testDir = HelperUtil.makeNewTestDir()
        archiveTask.baseName = 'testbasename'
        archiveTask.version = '1.0'
        archiveTask.destinationDir = new File('/destinationDir')
        archiveTask.resourceCollections = [new FileSet(testDir)]
        archiveTask.baseDir = testDir
    }

    void testExecute() {
        getAntMocker(true).use(ant) {
            archiveTask.execute()
        }
    }

    void testExecuteWithNullDestinationDir() {
        archiveTask.destinationDir = null
        shouldFailWithCause(InvalidUserDataException) {
            archiveTask.execute()
        }
    }

    void checkArchiveParameterEqualsArchive(AntArchiveParameter archiveParameter, AbstractArchiveTask task) {
        archiveParameter.ant.is(task.project.ant)
        archiveParameter.archiveName == "${task.baseName}-${task.version}.${task.extension}"
        archiveParameter.archiveName.is(task.project.ant)
        archiveParameter.destinationDir.is(task.destinationDir)
        archiveParameter.createIfEmpty == task.createIfEmpty
        archiveParameter.resourceCollections.is(task.resourceCollections)
    }

    void checkMetaArchiveParameterEqualsArchive(AntMetaArchiveParameter metaArchiveParameter, AbstractArchiveTask task) {
        checkArchiveParameterEqualsArchive(metaArchiveParameter, task)
        metaArchiveParameter.gradleManifest.is(task.manifest)
        metaArchiveParameter.metaInfFileSets.is(task.metaInfFileSets)
    }

    void testFileSetWithTaskBaseDir() {
        assertEquals(archiveTask.baseDir, archiveTask.fileSet().dir)
    }

    List getFileSetMethods() {
        ['fileSet']
    }

    void testFileSetWithSpecifiedBaseDir() {
        applyToFileSetMethods {
            File specifiedBaseDir = new File('/root')
            FileSet fileSet = archiveTask."$it"(dir: specifiedBaseDir)
            assertEquals(specifiedBaseDir, fileSet.dir)
            assert archiveTask.resourceCollections.contains(fileSet)
        }
    }

    void testFileSetWithTaskBaseDirAndConfigureClosure() {
        applyToFileSetMethods {
            String includePattern = 'a'
            Closure configureClosure = {
                include(includePattern)
            }
            FileSet fileSet = archiveTask."$it"(configureClosure)
            assert archiveTask.resourceCollections.contains(fileSet)
            assertEquals([includePattern] as Set, fileSet.includes)
        }
    }

    void testFiles() {
        Set files = ['a' as File, 'b' as File]
        FileCollection fileCollection = archiveTask.files(files as File[])
        assertTrue(archiveTask.resourceCollections.contains(fileCollection))
        assertEquals(files, fileCollection.files)
    }

    private void applyToFileSetMethods(Closure cl) {
        fileSetMethods.each {
            cl.call(it)
        }
    }

    void testArchivePath() {
        assertEquals(new File(archiveTask.destinationDir, archiveTask.archiveName), archiveTask.archivePath)
    }

}
