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

/**
 * @author Hans Dockter
 */
class InstallTest extends GroovyTestCase {
    File testDir
    Install install
    String distName
    String urlRoot
    IDownload downloadMock
    boolean downloadCalled
    File zip
    File rootDir
    File someScript
    File destFile

    void setUp() {
        downloadCalled = false
        testDir = HelperUtil.makeNewTestDir()
        rootDir = new File(testDir, 'gradle')
        install = new Install(false)
        distName = 'gradle-1.0'
        destFile = new File(rootDir, "${distName}.zip")
        urlRoot = 'file://./tmpTest'
        initDownloadMock()
    }

    void initDownloadMock() {
        downloadMock = [download: {String url, File destination ->
            assertEquals("$urlRoot/${distName}.zip", url)
            assertEquals(destFile, destination)
            zip = createTestZip()
            downloadCalled = true
        }] as IDownload
        install.setDownload(downloadMock)
    }

     void tearDown() {
        HelperUtil.deleteTestDir()
    }

    File createTestZip() {
        File explodedZipDir = new File(testDir, 'explodedZip')
        File binDir = new File(explodedZipDir, 'bin')
        binDir.mkdirs()
        someScript = new File(binDir, 'somescript')
        someScript.write('something')
        rootDir.mkdirs()
        AntBuilder antBuilder = new AntBuilder()
        antBuilder.zip(destfile: destFile) {
            zipfileset(dir: explodedZipDir, prefix: distName)
        }
        destFile
    }

    void testCreateDist() {
        install.createDist(urlRoot, distName, rootDir)
        assert downloadCalled
        File distFile = new File(rootDir, distName)
        assert distFile.isDirectory()
        assert someScript.exists()
    }

    void testCreateDistWithExistingRoot() {
        rootDir.mkdirs()
        File otherFile = new File(rootDir, 'other')
        otherFile.createNewFile()
        install.createDist(urlRoot, distName, rootDir)
        assert downloadCalled
        File distFile = new File(rootDir, distName)
        assert distFile.isDirectory()
        assert someScript.exists()
        assert !otherFile.exists()
    }

    void testCreateDistWithExistingDist() {
        File distFile = new File(rootDir, distName)
        distFile.mkdirs()
        long lastModified = distFile.lastModified()
        install.createDist(urlRoot, distName, rootDir)
        assert !downloadCalled
        assert lastModified == distFile.lastModified()
    }

    void testCreateDistWithExistingDistAndAlwaysInstallTrue() {
        install = new Install(true)
        initDownloadMock()
        File distFile = new File(rootDir, distName)
        distFile.mkdirs()
        long lastModified = distFile.lastModified()
        install.createDist(urlRoot, distName, rootDir)
        assert downloadCalled
    }
}
