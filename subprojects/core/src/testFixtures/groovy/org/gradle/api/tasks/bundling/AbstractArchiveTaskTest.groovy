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

import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.tasks.AbstractConventionTaskTest
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

/**
 * @author Hans Dockter
 */
abstract class AbstractArchiveTaskTest extends AbstractConventionTaskTest {

    FileResolver resolver = [resolve: {it as File}] as FileResolver

    abstract AbstractArchiveTask getArchiveTask()

    ConventionTask getTask() {
        archiveTask
    }

    void checkConstructor() {
        assertEquals('', archiveTask.classifier)
    }

    protected void configure(AbstractArchiveTask archiveTask) {
        archiveTask.baseName = 'testbasename'
        archiveTask.appendix = 'testappendix'
        archiveTask.version = '1.0'
        archiveTask.classifier = 'src'
        archiveTask.destinationDir = new File(tmpDir.testDirectory, 'destinationDir')
    }

    @Test public void testExecute() {
        archiveTask.execute()
        assertTrue(archiveTask.destinationDir.isDirectory())
        assertTrue(archiveTask.archivePath.isFile())
    }

    @Test public void testArchiveNameWithEmptyExtension() {
        archiveTask.extension = null
        assertEquals("testbasename-testappendix-1.0-src".toString(), archiveTask.archiveName)
    }

    @Test public void testArchiveNameWithEmptyBasename() {
        archiveTask.baseName = null
        assertEquals("testappendix-1.0-src.${archiveTask.extension}".toString(), archiveTask.archiveName)
    }

    @Test public void testArchiveNameWithEmptyBasenameAndAppendix() {
        archiveTask.baseName = null
        archiveTask.appendix = null
        assertEquals("1.0-src.${archiveTask.extension}".toString(), archiveTask.archiveName)
    }

    @Test public void testArchiveNameWithEmptyBasenameAndAppendixAndVersion() {
        archiveTask.baseName = null
        archiveTask.appendix = null
        archiveTask.version = null
        assertEquals("src.${archiveTask.extension}".toString(), archiveTask.archiveName)
    }

    @Test public void testArchiveNameWithEmptyBasenameAndAppendixAndVersionAndClassifier() {
        archiveTask.baseName = null
        archiveTask.appendix = null
        archiveTask.version = null
        archiveTask.classifier = null
        assertEquals(".${archiveTask.extension}".toString(), archiveTask.archiveName)
    }


    @Test public void testArchiveNameWithEmptyClassifier() {
        archiveTask.classifier = null
        assertEquals("testbasename-testappendix-1.0.${archiveTask.extension}".toString(), archiveTask.archiveName)
    }

    @Test public void testArchiveNameWithEmptyAppendix() {
        archiveTask.appendix = null
        assertEquals("testbasename-1.0-src.${archiveTask.extension}".toString(), archiveTask.archiveName)
    }

    @Test public void testArchiveNameWithEmptyVersion() {
        archiveTask.version = null
        assertEquals("testbasename-testappendix-src.${archiveTask.extension}".toString(), archiveTask.archiveName)
    }

    @Test public void testUsesCustomArchiveNameWhenSet() {
        archiveTask.archiveName = 'somefile.out'
        assertEquals('somefile.out', archiveTask.archiveName)
    }

    @Test public void testArchivePath() {
        assertEquals(new File(archiveTask.destinationDir, archiveTask.archiveName), archiveTask.archivePath)
    }
}
