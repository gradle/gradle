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

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import spock.lang.Specification

import static org.junit.Assert.assertEquals

/**
 * @author Hans Dockter
 */
class InstallTest extends Specification {
    File testDir
    Install install
    IDownload downloadMock
    PathAssembler pathAssemblerMock;
    boolean downloadCalled
    File zip
    TestFile distributionDir
    File zipStore
    TestFile gradleHomeDir
    TestFile zipDestination
    WrapperConfiguration configuration = new WrapperConfiguration()
    IDownload download = Mock()
    PathAssembler pathAssembler = Mock()
    PathAssembler.LocalDistribution localDistribution = Mock()
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();

    @Before public void setup() {
        downloadCalled = false
        testDir = tmpDir.testDirectory
        configuration.zipBase = PathAssembler.PROJECT_STRING
        configuration.zipPath = 'someZipPath'
        configuration.distributionBase = PathAssembler.GRADLE_USER_HOME_STRING
        configuration.distributionPath = 'someDistPath'
        configuration.distribution = new URI('http://server/gradle-0.9.zip')
        configuration.alwaysDownload = false
        configuration.alwaysUnpack = false
        distributionDir = new TestFile(testDir, 'someDistPath')
        gradleHomeDir = new TestFile(distributionDir, 'gradle-0.9')
        zipStore = new File(testDir, 'zips');
        zipDestination = new TestFile(zipStore, 'gradle-0.9.zip')
        install = new Install(download, pathAssembler)
    }

    IDownload createDownloadMock() {
        [download: {URI url, File destination ->
            assertEquals(configuration.distribution, url)
            assertEquals(zipDestination.getAbsolutePath() + '.part', destination.getAbsolutePath())
            zip = createTestZip()
            downloadCalled = true
        }] as IDownload
    }

    PathAssembler createPathAssemblerMock() {
        [gradleHome: {String distBase, String distPath, URI distUrl ->
            assertEquals(configuration.distributionBase, distBase)
            assertEquals(configuration.distributionPath, distPath)
            assertEquals(configuration.distribution, distUrl)
            gradleHomeDir},
         distZip: { String zipBase, String zipPath, URI distUrl ->
            assertEquals(configuration.zipBase, zipBase)
            assertEquals(configuration.zipPath, zipPath)
             assertEquals(configuration.distribution, distUrl)
            zipDestination
        }] as PathAssembler
    }

    void createTestZip(File zipDestination) {
        TestFile explodedZipDir = tmpDir.createDir('explodedZip')
        TestFile gradleScript = explodedZipDir.file('gradle-0.9/bin/gradle')
        gradleScript.parentFile.createDir()
        gradleScript.write('something')
        explodedZipDir.zipTo(new TestFile(zipDestination))
    }

    public void testCreateDist() {
        when:
        def homeDir = install.createDist(configuration)

        then:
        homeDir == gradleHomeDir
        gradleHomeDir.assertIsDir()
        gradleHomeDir.file("bin/gradle").assertIsFile()
        zipDestination.assertIsFile()

        and:
        1 * pathAssembler.getDistribution(configuration) >> localDistribution
        _ * localDistribution.distributionDir >> distributionDir
        _ * localDistribution.zipFile >> zipDestination
        1 * download.download(configuration.distribution, _) >> { createTestZip(it[1]) }
        0 * download._
    }

    @Test public void testCreateDistWithExistingDistribution() {
        given:
        zipDestination.createFile()
        gradleHomeDir.file('some-file').createFile()
        gradleHomeDir.createDir()

        when:
        def homeDir = install.createDist(configuration)

        then:
        homeDir == gradleHomeDir
        gradleHomeDir.assertIsDir()
        gradleHomeDir.file('some-file').assertIsFile()
        zipDestination.assertIsFile()

        and:
        1 * pathAssembler.getDistribution(configuration) >> localDistribution
        _ * localDistribution.distributionDir >> distributionDir
        _ * localDistribution.zipFile >> zipDestination
        0 * download._
    }

    @Test public void testCreateDistWithExistingDistAndZipAndAlwaysUnpackTrue() {
        given:
        createTestZip(zipDestination)
        gradleHomeDir.file('garbage').createFile()
        configuration.alwaysUnpack = true

        when:
        def homeDir = install.createDist(configuration)

        then:
        homeDir == gradleHomeDir
        gradleHomeDir.assertIsDir()
        gradleHomeDir.file('garbage').assertDoesNotExist()
        zipDestination.assertIsFile()

        and:
        1 * pathAssembler.getDistribution(configuration) >> localDistribution
        _ * localDistribution.distributionDir >> distributionDir
        _ * localDistribution.zipFile >> zipDestination
        0 * download._
    }

    @Test public void testCreateDistWithExistingZipAndDistAndAlwaysDownloadTrue() {
        given:
        createTestZip(zipDestination)
        gradleHomeDir.file('garbage').createFile()
        configuration.alwaysDownload = true

        when:
        def homeDir = install.createDist(configuration)

        then:
        homeDir == gradleHomeDir
        gradleHomeDir.assertIsDir()
        gradleHomeDir.file("bin/gradle").assertIsFile()
        gradleHomeDir.file('garbage').assertDoesNotExist()
        zipDestination.assertIsFile()

        and:
        1 * pathAssembler.getDistribution(configuration) >> localDistribution
        _ * localDistribution.distributionDir >> distributionDir
        _ * localDistribution.zipFile >> zipDestination
        1 * download.download(configuration.distribution, _) >> { createTestZip(it[1]) }
        0 * download._
    }
}
