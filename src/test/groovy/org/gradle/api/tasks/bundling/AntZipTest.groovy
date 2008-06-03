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

import org.gradle.api.tasks.util.FileSet

/**
 * @author Hans Dockter
 */
class AntZipTest extends AbstractAntSkippableArchiveTest {

    AntZip antZip

    void setUp() {
        super.setUp()
        antZip = new AntZip()
    }

    void testExecute() {
        antZip.execute(new AntArchiveParameter(resourceCollections, mergeFileSets, mergeGroupFileSets, true, testDir, archiveName, new AntBuilder()))
        unzipArchive()
        checkResourceFiles()
    }

    void executeWithEmptyFileList(boolean createIfEmpty) {
        antZip.execute(new AntArchiveParameter([new FileSet(emptyDir)], [], [], createIfEmpty, testDir, archiveName, new AntBuilder()))
    }
}