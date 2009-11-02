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
import org.gradle.api.tasks.util.FileSet
import org.junit.Test
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
        archiveTask.execute()
        assertTrue(archiveTask.destinationDir.isDirectory())
        assertTrue(archiveTask.archivePath.isFile())
    }

    @Test public void testArchivePath() {
        assertEquals(new File(archiveTask.destinationDir, archiveTask.archiveName), archiveTask.archivePath)
    }
}
