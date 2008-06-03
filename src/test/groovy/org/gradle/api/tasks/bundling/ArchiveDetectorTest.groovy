/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.api.tasks.util.ZipFileSet
import org.gradle.api.tasks.util.TarFileSet

/**
 * @author Hans Dockter
 */
class ArchiveDetectorTest extends GroovyTestCase {
    ArchiveDetector archiveDetector

    void setUp() {
        archiveDetector = new ArchiveDetector()
    }

    void testArchiveFileSetType() {
        List zipFiles = ['a.zip', 'b.jar', 'c.war', 'd.ear']
        zipFiles.each {
            assertEquals(ZipFileSet, archiveDetector.archiveFileSetType(it as File))
            assertTrue archiveDetector.isZipArchive(it)
            assertFalse archiveDetector.isTarArchive(it)
        }
        List tarFiles = ["a.tar", "b.tar.gz", "c.tgz", "d.tar.bz", "e.tbz2"]
        tarFiles.each {
            assertEquals(TarFileSet, archiveDetector.archiveFileSetType(it as File))
            assertFalse archiveDetector.isZipArchive(it)
            assertTrue archiveDetector.isTarArchive(it)
        }
    }

    void testUnknownArchive() {
        assertNull archiveDetector.archiveFileSetType('k' as File)
        assertNull archiveDetector.archiveFileSetType('k.tip' as File)
    }
}
