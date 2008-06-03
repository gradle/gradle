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
class AntJarTest extends AbstractAntSkippableArchiveTest {
    AntJar antJar

    void setUp() {
        super.setUp()
        antJar = new AntJar()
    }

    void testExecute() {
        antJar.execute(new AntMetaArchiveParameter(resourceCollections, mergeFileSets, mergeGroupFileSets, '', true, testDir,
                archiveName, manifest, createFileSetDuo(AbstractAntArchiveTest.METAINFS_KEY), new AntBuilder()))
        unzipArchive()
        checkResourceFiles()
        checkMetaData()
    }

    void testExecuteWithNullManifestAndNullMetaInf() {
        antJar.execute(new AntMetaArchiveParameter(resourceCollections, mergeFileSets, mergeGroupFileSets, '', true, testDir,
                archiveName, null, null, new AntBuilder()))
        unzipArchive()
        checkResourceFiles()
    }

    void testExecuteWithManifestNullFile() {
        antJar.execute(new AntMetaArchiveParameter(resourceCollections, mergeFileSets, mergeGroupFileSets, '', true, testDir,
                archiveName, new GradleManifest(), null, new AntBuilder()))
        unzipArchive()
        checkResourceFiles()
    }

    void executeWithEmptyFileList(boolean createIfEmpty) {
        antJar.execute(new AntMetaArchiveParameter([new FileSet(emptyDir)], [], [], '', createIfEmpty, testDir, archiveName, null, null, new AntBuilder()))
    }

}
