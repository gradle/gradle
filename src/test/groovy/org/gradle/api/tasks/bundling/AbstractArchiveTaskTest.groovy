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
import org.gradle.api.GradleScriptException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.AbstractTask
import org.gradle.api.tasks.AbstractConventionTaskTest
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.AntArchiveParameter
import org.gradle.api.tasks.bundling.AntMetaArchiveParameter
import org.gradle.api.tasks.bundling.ArchiveDetector
import org.gradle.api.tasks.util.AntDirective
import org.gradle.api.tasks.util.FileSet
import org.gradle.api.tasks.util.ZipFileSet
import org.gradle.util.HelperUtil
import org.junit.Before
import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import static org.gradle.util.Matchers.*
import org.gradle.api.artifacts.FileCollection
import org.gradle.api.tasks.util.AntFileCollectionBuilder

/**
 * @author Hans Dockter
 */
abstract class AbstractArchiveTaskTest extends AbstractConventionTaskTest {

    abstract AbstractArchiveTask getArchiveTask()

    AbstractTask getTask() {
        archiveTask
    }

    abstract MockFor getAntMocker(boolean toBeCalled)

    abstract def getAnt()

    @Before public void setUp() {
        super.setUp()
    }

    void checkConstructor() {
        assertFalse(archiveTask.createIfEmpty)
        assertNull(archiveTask.resourceCollections)
        assertEquals([], archiveTask.mergeFileSets)
        assertEquals([], archiveTask.mergeGroupFileSets)
        assertEquals('', archiveTask.classifier)
    }

    protected void configure(AbstractArchiveTask archiveTask) {
        File testDir = HelperUtil.makeNewTestDir()
        archiveTask.baseName = 'testbasename'
        archiveTask.appendix = 'testappendix'
        archiveTask.version = '1.0'
        archiveTask.classifier = 'src'
        archiveTask.destinationDir = new File(testDir, 'destinationDir')
        archiveTask.resourceCollections = [new FileSet(testDir)]
        archiveTask.baseDir = testDir

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

    @Test (expected = GradleScriptException) public void testExecuteWithNullDestinationDir() {
        archiveTask.destinationDir = null
        archiveTask.execute()
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
            File specifiedBaseDir = new File('/root')
            FileSet fileSet = archiveTask."$it"(dir: specifiedBaseDir)
            assertEquals(specifiedBaseDir, fileSet.dir)
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
        List files = ['a' as File, 'b' as File]
        FileCollection fileCollection = archiveTask.files(files as File[])
        assertThat(archiveTask.resourceCollections, hasItem(reflectionEquals(new AntFileCollectionBuilder(fileCollection))))
        assertEquals(files.collect(new LinkedHashSet()) {it.canonicalFile}, fileCollection.files)
    }

    @Test public void testIncludeFileCollection() {
        FileCollection fileCollection = [:] as FileCollection
        assertSame(fileCollection, archiveTask.files(fileCollection))
        assertThat(archiveTask.resourceCollections, hasItem(reflectionEquals(new AntFileCollectionBuilder(fileCollection))))
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

    @Test public void testMerge() {
        archiveTask.archiveDetector = [archiveFileSetType: {File file -> ZipFileSet }] as ArchiveDetector
        List fileDescriptions = ['a.zip' as File, new File(HelperUtil.TMP_DIR_FOR_TEST, 'b.zip').absolutePath]
        assert archiveTask.merge(fileDescriptions) {
            include('x')
        }.is(archiveTask)
        List mergeFileSets = archiveTask.mergeFileSets
        assertEquals(fileDescriptions.size(), mergeFileSets.size())
        assert mergeFileSets[0] instanceof ZipFileSet
        assertEquals(new File(HelperUtil.TMP_DIR_FOR_TEST, 'a.zip').absoluteFile, mergeFileSets[0].dir)
        assertEquals(['x'] as Set, mergeFileSets[0].includes)
        assert mergeFileSets[1] instanceof ZipFileSet
        assertEquals(['x'] as Set, mergeFileSets[1].includes)
    }

    @Test public void testMergeWithoutClosure() {
        archiveTask.archiveDetector = [archiveFileSetType: {File file -> ZipFileSet }] as ArchiveDetector
        assert archiveTask.merge('a.zip').is(archiveTask)
        List mergeFileSets = archiveTask.mergeFileSets
        assertEquals(1, mergeFileSets.size())
        assert mergeFileSets[0] instanceof ZipFileSet
        assertEquals(new File(HelperUtil.TMP_DIR_FOR_TEST, 'a.zip').absoluteFile, mergeFileSets[0].dir)
    }

    @Test public void testMergeWithListArguments() {

    }

    @Test (expected = InvalidUserDataException) public void testMergeWithNonArchive() {
        archiveTask.archiveDetector = [archiveFileSetType: {File file -> null }] as ArchiveDetector
        archiveTask.merge('x')
    }

    @Test public void testMergeGroup() {
        Object[] fileDescriptions = [new File('a'), new File('b').absolutePath]

        assert archiveTask.mergeGroup(HelperUtil.TMP_DIR_FOR_TEST) {
            include('a')
        }.is(archiveTask)

        List mergeGroups = archiveTask.mergeGroupFileSets
        assertEquals(1, mergeGroups.size())
        assertEquals(mergeGroups[0].dir, HelperUtil.TMP_DIR_FOR_TEST as File)
        assertEquals(mergeGroups[0].includes, ['a'] as Set)
    }

}
