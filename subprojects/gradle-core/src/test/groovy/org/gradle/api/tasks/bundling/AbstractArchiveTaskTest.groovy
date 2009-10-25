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
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.tasks.AbstractConventionTaskTest
import org.gradle.api.tasks.util.AntDirective
import org.gradle.api.tasks.util.FileSet
import org.junit.Test
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
abstract class AbstractArchiveTaskTest extends AbstractConventionTaskTest {

    FileResolver resolver = [resolve: {it as File}] as FileResolver
    
    abstract AbstractArchiveTask getArchiveTask()

    ConventionTask getTask() {
        archiveTask
    }

    abstract MockFor getAntMocker(boolean toBeCalled)

    abstract def getAnt()

    void checkConstructor() {
        assertFalse(archiveTask.createIfEmpty)
        assertEquals([], archiveTask.resourceCollections)
        assertEquals('', archiveTask.classifier)
    }

    protected void configure(AbstractArchiveTask archiveTask) {
        archiveTask.baseName = 'testbasename'
        archiveTask.appendix = 'testappendix'
        archiveTask.version = '1.0'
        archiveTask.classifier = 'src'
        archiveTask.destinationDir = new File(tmpDir.dir, 'destinationDir')
        archiveTask.resourceCollections = [new FileSet(tmpDir.dir, resolver)]
        archiveTask.baseDir = tmpDir.dir
    }

    @Test public void testExecute() {
        checkExecute {archiveTask ->}
    }

    @Test public void testExecuteWithEmptyClassifier() {
        checkExecute {archiveTask -> archiveTask.classifier = null}
    }

    @Test public void testExecuteWithEmptyAppendix() {
        checkExecute {archiveTask -> archiveTask.appendix = null}
    }

    private checkExecute(Closure archiveTaskModifier) {
        archiveTaskModifier.call(archiveTask)
        getAntMocker(true).use(ant) {
            archiveTask.execute()
        }
        assertTrue(archiveTask.destinationDir.isDirectory())
    }

    void checkArchiveParameterEqualsArchive(AntArchiveParameter archiveParameter, AbstractArchiveTask task) {
        assert archiveParameter.ant.is(task.project.ant)
        String classifierSnippet = task.classifier ? '-' + task.classifier : ''
        String appendixSnippet = task.appendix ? '-' + task.appendix : ''
        assert archiveParameter.archiveName == "${task.baseName}${appendixSnippet}-${task.version}${classifierSnippet}.${task.extension}"
        assert archiveParameter.destinationDir.is(task.destinationDir)
        assert archiveParameter.createIfEmpty == task.createIfEmpty
        assert archiveParameter.resourceCollections.is(task.resourceCollections)
    }

    void checkMetaArchiveParameterEqualsArchive(AntMetaArchiveParameter metaArchiveParameter, AbstractArchiveTask task) {
        checkArchiveParameterEqualsArchive(metaArchiveParameter, task)
        assert metaArchiveParameter.gradleManifest.is(task.manifest)
        assert metaArchiveParameter.metaInfFileSets.is(task.metaInfResourceCollections)
    }

    @Test public void testFileSetWithTaskBaseDir() {
        assertEquals(archiveTask.baseDir, archiveTask.fileSet().dir)
    }

    List getFileSetMethods() {
        ['fileSet']
    }

    @Test public void testFileSetWithSpecifiedBaseDir() {
        applyToFileSetMethods {
            File specifiedBaseDir = new File('baseDir')
            FileSet fileSet = archiveTask."$it"(dir: specifiedBaseDir)
            assertEquals(project.file(specifiedBaseDir), fileSet.dir)
            assert archiveTask.resourceCollections.contains(fileSet)
        }
    }

    @Test public void testFileSetWithTaskBaseDirAndConfigureClosure() {
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

    @Test public void testFiles() {
        FileCollection fileCollection = archiveTask.from('a', 'b')
        assertThat(archiveTask.resourceCollections, hasItem(fileCollection))
        assertEquals([project.file('a'), project.file('b')], fileCollection as List)
    }

    @Test public void testIncludeFileCollection() {
        FileCollection fileCollection = archiveTask.from([:] as FileCollection)
        assertThat(archiveTask.resourceCollections, hasItem(fileCollection))
    }

    @Test public void testAntDirective() {
        Closure expectedDirective = {}
        AntDirective antDirective = archiveTask.antDirective(expectedDirective)
        assertTrue(archiveTask.resourceCollections.contains(antDirective))
        assert antDirective.directive.is(expectedDirective)

    }

    private void applyToFileSetMethods(Closure cl) {
        fileSetMethods.each {
            cl.call(it)
        }
    }

    @Test public void testArchivePath() {
        assertEquals(new File(archiveTask.destinationDir, archiveTask.archiveName), archiveTask.archivePath)
    }
}
