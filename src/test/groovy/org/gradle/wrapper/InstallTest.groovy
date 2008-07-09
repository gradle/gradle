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

import org.gradle.util.HelperUtil
import static org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.gradle.api.tasks.wrapper.Wrapper.PathBase;

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
    String testDistName
    String testDistVersion
    String testDistClassifier

    String urlRoot
    IDownload downloadMock
    PathAssembler pathAssemblerMock;
    boolean downloadCalled
    File zip
    File distributionDir
    File zipStore
    File someScript
    File gradleHomeDir
    File zipDestination

    @Before public void setUp() {
        downloadCalled = false
        testDir = HelperUtil.makeNewTestDir()
        testZipBase = PathBase.PROJECT.toString()
        testZipPath = 'someZipPath'
        testDistBase = PathBase.GRADLE_USER_HOME.toString()
        testDistPath = 'someDistPath'
        testDistName = 'gradle'
        testDistVersion = '1.0'
        testDistClassifier = 'clf'
        distributionDir = new File(testDir, testDistPath)
        gradleHomeDir = new File(distributionDir, "$testDistName-$testDistVersion")
        zipStore = new File(testDir, 'zips');
        zipDestination = new File(zipStore, "$testDistName-$testDistVersion-$testDistClassifier" + ".zip")
        urlRoot = 'file://./tmpTest'
        install = new Install(false, false, createDownloadMock(), createPathAssemblerMock())
    }

    IDownload createDownloadMock() {
        [download: {String url, File destination ->
            assertEquals("$urlRoot/$testDistName-${testDistVersion}-${testDistClassifier}.zip" as String, url)
            assertEquals(zipDestination.getAbsolutePath(), destination.getAbsolutePath())
            zip = createTestZip()
            downloadCalled = true
        }] as IDownload
    }

    PathAssembler createPathAssemblerMock() {
        [gradleHome: {String distBase, String distPath, String distName, String distVersion ->
            assertEquals(testDistBase, distBase)
            assertEquals(testDistPath, distPath)
            assertEquals(testDistName, distName)
            assertEquals(testDistVersion, distVersion)
            gradleHomeDir.getAbsolutePath()},
         distZip: { String zipBase, String zipPath, String distName, String distVersion, String distClassifier ->
            assertEquals(testZipBase, zipBase)
            assertEquals(testZipPath, zipPath)
            assertEquals(testDistName, distName)
            assertEquals(testDistVersion, distVersion)
            assertEquals(testDistClassifier, distClassifier)
            zipDestination.getAbsolutePath()
        }] as PathAssembler
    }

    @After
    public void tearDown() {
        //HelperUtil.deleteTestDir()
    }

    @Test public void testInit() {
        assert !install.alwaysDownload
        assert !install.alwaysUnpack
    }

    File createTestZip() {
        File explodedZipDir = new File(testDir, 'explodedZip')
        File binDir = new File(explodedZipDir, 'bin')
        binDir.mkdirs()
        someScript = new File(binDir, 'somescript')
        someScript.write('something')
        zipStore.mkdirs()
        AntBuilder antBuilder = new AntBuilder()
        antBuilder.zip(destfile: zipDestination) {
            zipfileset(dir: explodedZipDir, prefix: "$testDistName-$testDistVersion")
        }
        zipDestination
    }

    @Test public void testCreateDist() {
        assertEquals(gradleHomeDir.absolutePath, install.createDist(urlRoot, testDistBase, testDistPath, testDistName, testDistVersion, testDistClassifier, testZipBase, testZipPath))
        assert downloadCalled
        assert distributionDir.isDirectory()
        assert someScript.exists()
    }

    @Test public void testCreateDistWithExistingRoot() {
        distributionDir.mkdirs()
        install.createDist(urlRoot, testDistBase, testDistPath, testDistName, testDistVersion, testDistClassifier, testZipBase, testZipPath)
        assert downloadCalled
        assert gradleHomeDir.isDirectory()
        assert someScript.exists()
    }

    @Test public void testCreateDistWithExistingDist() {
        gradleHomeDir.mkdirs()
        long lastModified = gradleHomeDir.lastModified()
        install.createDist(urlRoot, testDistBase, testDistPath, testDistName, testDistVersion, testDistClassifier, testZipBase, testZipPath)
        assert !downloadCalled
        assert lastModified == gradleHomeDir.lastModified()
    }

    @Test public void testCreateDistWithExistingDistAndZipAndAlwaysUnpackTrue() {
        install = new Install(false, true, createDownloadMock(), createPathAssemblerMock())
        createTestZip()
        gradleHomeDir.mkdirs()
        File testFile = new File(gradleHomeDir, 'testfile')
        install.createDist(urlRoot, testDistBase, testDistPath, testDistName, testDistVersion, testDistClassifier, testZipBase, testZipPath)
        assert distributionDir.isDirectory()
        assert someScript.exists()
        assert !testFile.exists()
        assert !downloadCalled
    }

    @Test public void testCreateDistWithExistingZipAndDistAndAlwaysDownloadTrue() {
        install = new Install(true, false, createDownloadMock(), createPathAssemblerMock())
        createTestZip()
        distributionDir.mkdirs()
        File testFile = new File(gradleHomeDir, 'testfile')
        install.createDist(urlRoot, testDistBase, testDistPath, testDistName, testDistVersion, testDistClassifier, testZipBase, testZipPath)
        assert gradleHomeDir.isDirectory()
        assert someScript.exists()
        assert !testFile.exists()
        assert downloadCalled
    }
}
