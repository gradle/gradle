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


import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.ClassRule
import org.junit.Rule
import spock.lang.Shared
import spock.lang.Specification

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@CleanupTestDirectory
class InstallTest extends Specification {
    public static final String FETCHED_HASH = "fetchedHash"
    WrapperConfiguration configuration = new WrapperConfiguration()

    private final String zipFileName = 'gradle-0.9.zip';

    File testDir
    TestFile distributionDir
    TestFile gradleHomeDir
    File zipStore
    TestFile zipDestination

    IDownload download = Mock()
    PathAssembler pathAssembler = Mock()
    PathAssembler.LocalDistribution localDistribution = Mock()
    Install install
    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    @Shared @ClassRule TestNameTestDirectoryProvider sharedTemporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())
    @Shared TestFile templateZipFile = new TestFile(sharedTemporaryFolder.testDirectory, "template-gradle.zip")
    @Shared TestFile templateEvalZipFile = new TestFile(sharedTemporaryFolder.testDirectory, "template-eval-gradle.zip")
    @Shared String templateZipHash
    @Shared String templateEvalZipHash

    void setupSpec() {
        createTestZip(templateZipFile)
        templateZipHash = Install.calculateSha256Sum(templateZipFile)
        createEvilZip(templateEvalZipFile)
        templateEvalZipHash = Install.calculateSha256Sum(templateEvalZipFile)
    }

    void setup() {
        initConfiguration()

        testDir = temporaryFolder.testDirectory
        distributionDir = new TestFile(testDir, 'someDistPath')
        gradleHomeDir = new TestFile(distributionDir, 'gradle-0.9')
        zipStore = new File(testDir, 'zips')
        zipDestination = new TestFile(zipStore, zipFileName)
        install = new Install(new Logger(true), download, pathAssembler)
    }

    private void initConfiguration() {
        configuration.zipBase = PathAssembler.PROJECT_STRING
        configuration.zipPath = 'someZipPath'
        configuration.distributionBase = PathAssembler.GRADLE_USER_HOME_STRING
        configuration.distributionPath = 'someDistPath'
        configuration.distribution = new URI("http://server/$zipFileName")
    }

    void createTestZip(File zipDestination) {
        TestFile explodedZipDir = sharedTemporaryFolder.createDir('explodedZip')
        TestFile gradleScript = explodedZipDir.file('gradle-0.9/bin/gradle')
        gradleScript.parentFile.createDir()
        gradleScript.write('something')
        TestFile gradleLauncherJar = explodedZipDir.file('gradle-0.9/lib/gradle-launcher-0.9.jar')
        gradleLauncherJar.parentFile.createDir()
        gradleLauncherJar.write('something')
        explodedZipDir.zipTo(new TestFile(zipDestination))
        explodedZipDir.deleteDir();
    }

    def "installs distribution and reuses on subsequent access"() {
        given:
        _ * pathAssembler.getDistribution(configuration) >> localDistribution
        _ * localDistribution.distributionDir >> distributionDir
        _ * localDistribution.zipFile >> zipDestination

        when:
        def homeDir = install.createDist(configuration)

        then:
        homeDir == gradleHomeDir
        gradleHomeDir.assertIsDir()
        gradleHomeDir.file("bin/gradle").assertIsFile()

        and:
        1 * download.download(configuration.distribution, _) >> { templateZipFile.copyTo(it[1]) }
        0 * download._

        when:
        homeDir = install.createDist(configuration)

        then:
        homeDir == gradleHomeDir

        and:
        0 * download._
    }

    def "recovers from download failure"() {
        def failure = new RuntimeException("broken")

        given:
        _ * pathAssembler.getDistribution(configuration) >> localDistribution
        _ * localDistribution.distributionDir >> distributionDir
        _ * localDistribution.zipFile >> zipDestination

        when:
        install.createDist(configuration)

        then:
        RuntimeException e = thrown()
        e == failure

        and:
        1 * download.download(configuration.distribution, _) >> {
            it[1].text = 'broken!'
            throw failure
        }
        0 * download._

        when:
        def homeDir = install.createDist(configuration)

        then:
        homeDir == gradleHomeDir

        and:
        1 * download.download(configuration.distribution, _) >> { templateZipFile.copyTo(it[1]) }
        0 * download._
    }

    def "refuses to install distribution with unsafe zip entry name"() {
        given:
        _ * pathAssembler.getDistribution(configuration) >> localDistribution
        _ * localDistribution.distributionDir >> distributionDir
        _ * localDistribution.zipFile >> zipDestination

        when:
        install.createDist(configuration)

        then:
        def failure = thrown(IllegalArgumentException)
        failure.message == "'../../tmp/evil.sh' is not a safe zip entry name."

        and:
        1 * download.download(configuration.distribution, _) >> { createEvilZip(it[1]) }
        0 * download._
    }

    def "successfully get distribution hash"() {
        when:

        def hash = install.fetchDistributionSha256Sum(configuration, new TestFile(zipStore, zipFileName))

        then:

        hash == FETCHED_HASH

        and:
        1 * download.download(distributionHashUri, _) >> { createFile(it[1]) }
        0 * download._
    }

    private getDistributionHashUri() {
        new URI(configuration.distribution.toString() + ".sha256")
    }

    def "fail on getting distribution hash"() {
        when:

        def hash = install.fetchDistributionSha256Sum(configuration, new TestFile(zipStore, zipFileName))

        then:

        hash == null

        and:
        1 * download.download(distributionHashUri, _) >> { throw new Exception() }
        0 * download._
    }


    void createFile(File file) {
        file.parentFile.mkdirs()
        new FileOutputStream(file).withCloseable { FileOutputStream fos ->
            fos.write(FETCHED_HASH.getBytes())
        }
    }

    static void createEvilZip(File zipDestination) {
        zipDestination.withOutputStream {
            new ZipOutputStream(it).withCloseable { ZipOutputStream zos ->
                zos.putNextEntry(new ZipEntry('../../tmp/evil.sh'))
                zos.write("evil".getBytes('utf-8'))
                zos.closeEntry()
            }
        }
    }
}
