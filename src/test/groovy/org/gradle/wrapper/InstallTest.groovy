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
import org.junit.Test;

/**
 * @author Hans Dockter
 */
class InstallTest {
    File testDir
    Install install
    String distName
    String urlRoot
    IDownload downloadMock
    boolean downloadCalled
    File zip
    File distributionPath
    File zipStore
    File someScript
    File distDir
    File destFile

    @Before public void setUp()  {
        downloadCalled = false
        testDir = HelperUtil.makeNewTestDir()
        distributionPath = new File(testDir, 'gradle')
        install = new Install(false, false)
        distName = 'gradle-1.0'
        zipStore = new File(testDir, 'zips');
        destFile = new File(zipStore, "$distName" + ".zip")
        urlRoot = 'file://./tmpTest'
        initDownloadMock()
        distDir = new File(distributionPath, distName)
    }

    void initDownloadMock() {
        downloadMock = [download: {String url, File destination ->
            assertEquals("$urlRoot/${distName}.zip" as String, url)
            assertEquals(destFile.getAbsolutePath(), destination.getAbsolutePath())
            zip = createTestZip()
            downloadCalled = true
        }] as IDownload
        install.setDownload(downloadMock)
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir()
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
        antBuilder.zip(destfile: destFile) {
            zipfileset(dir: explodedZipDir, prefix: distName)
        }
        destFile
    }

    @Test public void testCreateDist() {
        install.createDist(urlRoot, distributionPath, distName, zipStore)
        assert downloadCalled
        assert distDir.isDirectory()
        assert someScript.exists()
    }

    @Test public void testCreateDistWithExistingRoot() {
        distributionPath.mkdirs()
        File otherFile = new File(distributionPath, 'other')
        otherFile.createNewFile()
        install.createDist(urlRoot, distributionPath, distName, zipStore)
        assert downloadCalled
        assert distDir.isDirectory()
        assert someScript.exists()
    }

    @Test public void testCreateDistWithExistingDist() {
        distDir.mkdirs()
        long lastModified = distDir.lastModified()
        install.createDist(urlRoot, distributionPath, distName, zipStore)
        assert !downloadCalled
        assert lastModified == distDir.lastModified()
    }

    @Test public void testCreateDistWithExistingDistAndZipAndAlwaysUnpackTrue() {
        install = new Install(false, true)
        initDownloadMock()
        createTestZip()
        distDir.mkdirs()
        File testFile = new File(distDir, 'testfile')
        install.createDist(urlRoot, distributionPath, distName, zipStore)
        assert distDir.isDirectory()
        assert someScript.exists()
        assert !testFile.exists()
        assert !downloadCalled
    }

    @Test public void testCreateDistWithExistingZipAndDistAndAlwaysDownloadTrue() {
        install = new Install(true, false)
        initDownloadMock()
        createTestZip()
        distDir.mkdirs()
        File testFile = new File(distDir, 'testfile')
        install.createDist(urlRoot, distributionPath, distName, zipStore)
        assert distDir.isDirectory()
        assert someScript.exists()
        assert !testFile.exists()
        assert downloadCalled
    }
}
