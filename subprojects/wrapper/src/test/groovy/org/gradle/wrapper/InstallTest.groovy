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

package org.gradle.wrapper

import org.gradle.api.tasks.wrapper.Wrapper.PathBase
import org.gradle.util.TemporaryFolder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import static org.junit.Assert.assertEquals

/**
 * @author Hans Dockter
 */
class InstallTest {
    File testDir
    Install install
    String testZipBase
    String testZipPath
    String testDistBase
    String testDistPath
    URI testDistUrl
    IDownload downloadMock
    PathAssembler pathAssemblerMock;
    boolean downloadCalled
    File zip
    File distributionDir
    File zipStore
    File gradleScript
    File gradleHomeDir
    File zipDestination
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Before public void setUp() {
        downloadCalled = false
        testDir = tmpDir.dir
        testZipBase = PathBase.PROJECT.toString()
        testZipPath = 'someZipPath'
        testDistBase = PathBase.GRADLE_USER_HOME.toString()
        testDistPath = 'someDistPath'
        testDistUrl = new URI('http://server/gradle-0.9.zip')
        distributionDir = new File(testDir, testDistPath)
        gradleHomeDir = new File(distributionDir, 'gradle-0.9')
        zipStore = new File(testDir, 'zips');
        zipDestination = new File(zipStore, 'gradle-0.9.zip')
        install = new Install(false, false, createDownloadMock(), createPathAssemblerMock())
    }

    IDownload createDownloadMock() {
        [download: {URI url, File destination ->
            assertEquals(testDistUrl, url)
            assertEquals(zipDestination.getAbsolutePath() + '.part', destination.getAbsolutePath())
            zip = createTestZip()
            downloadCalled = true
        }] as IDownload
    }

    PathAssembler createPathAssemblerMock() {
        [gradleHome: {String distBase, String distPath, URI distUrl ->
            assertEquals(testDistBase, distBase)
            assertEquals(testDistPath, distPath)
            assertEquals(testDistUrl, distUrl)
            gradleHomeDir},
         distZip: { String zipBase, String zipPath, URI distUrl ->
            assertEquals(testZipBase, zipBase)
            assertEquals(testZipPath, zipPath)
             assertEquals(testDistUrl, distUrl)
            zipDestination
        }] as PathAssembler
    }

    @Test public void testInit() {
        assert !install.alwaysDownload
        assert !install.alwaysUnpack
    }

    File createTestZip() {
        File explodedZipDir = new File(testDir, 'explodedZip')
        File binDir = new File(explodedZipDir, 'bin')
        binDir.mkdirs()
        gradleScript = new File(binDir, 'gradle')
        gradleScript.write('something')
        zipStore.mkdirs()
        AntBuilder antBuilder = new AntBuilder()
        antBuilder.zip(destfile: zipDestination.absolutePath + '.part') {
            zipfileset(dir: explodedZipDir, prefix: 'gradle-0.9')
        }
        (zipDestination.absolutePath + '.part') as File
    }

    @Test public void testCreateDist() {
        assertEquals(gradleHomeDir, install.createDist(testDistUrl, testDistBase, testDistPath, testZipBase, testZipPath))
        assert downloadCalled
        assert distributionDir.isDirectory()
        assert zipDestination.exists()
        assert gradleScript.exists()
//        assert new File(gradleHomeDir, "bin/gradle").canExecute()
    }

    @Test public void testCreateDistWithExistingRoot() {
        distributionDir.mkdirs()
        install.createDist(testDistUrl, testDistBase, testDistPath, testZipBase, testZipPath)
        assert downloadCalled
        assert gradleHomeDir.isDirectory()
        assert gradleScript.exists()
    }

    @Test public void testCreateDistWithExistingDist() {
        gradleHomeDir.mkdirs()
        long lastModified = gradleHomeDir.lastModified()
        install.createDist(testDistUrl, testDistBase, testDistPath, testZipBase, testZipPath)
        assert !downloadCalled
        assert lastModified == gradleHomeDir.lastModified()
    }

    @Test public void testCreateDistWithExistingDistAndZipAndAlwaysUnpackTrue() {
        install = new Install(false, true, createDownloadMock(), createPathAssemblerMock())
        createTestZip().renameTo(zipDestination)
        gradleHomeDir.mkdirs()
        File testFile = new File(gradleHomeDir, 'testfile')
        install.createDist(testDistUrl, testDistBase, testDistPath, testZipBase, testZipPath)
        assert distributionDir.isDirectory()
        assert gradleScript.exists()
        assert !testFile.exists()
        assert !downloadCalled
    }

    @Test public void testCreateDistWithExistingZipAndDistAndAlwaysDownloadTrue() {
        install = new Install(true, false, createDownloadMock(), createPathAssemblerMock())
        createTestZip().renameTo(zipDestination)
        distributionDir.mkdirs()
        File testFile = new File(gradleHomeDir, 'testfile')
        install.createDist(testDistUrl, testDistBase, testDistPath, testZipBase, testZipPath)
        assert gradleHomeDir.isDirectory()
        assert gradleScript.exists()
        assert !testFile.exists()
        assert downloadCalled
    }
}
