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


import org.apache.tools.tar.TarEntry
import org.apache.tools.tar.TarOutputStream
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.ClassRule
import org.junit.Rule
import spock.lang.Shared
import spock.lang.Specification

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import static org.gradle.internal.hash.Hashing.sha256

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

    @Shared
    @ClassRule
    TestNameTestDirectoryProvider sharedTemporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())
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
        failure.message == "'../../tmp/evil.sh' is not a safe archive entry or path name."

        and:
        1 * download.download(configuration.distribution, _) >> { createEvilZip(it[1]) }
        0 * download._
    }

    private static final String BAD_ARCHIVE_CONTENT = "bad archive content"
    private static final byte[] RANDOM_ARCHIVE_CONTENT = new byte[]{182, 1, 128, 190, 122, 230, 21, 36, 231, 12, 76, 55, 153, 47, 240, 243, 134, 36, 10, 184, 61, 55, 6, 64, 230, 153, 135, 132,
        7, 239, 222, 197, 147, 196, 58, 84, 178, 49, 167, 116, 21, 100, 66, 160, 138, 91, 98, 247, 25, 124, 43, 39, 195, 86, 103, 117, 5, 152, 111, 3, 3, 42, 169, 64, 41, 64, 104, 3, 105, 4, 99, 210,
        14, 108, 117, 74, 201, 213, 28, 246, 53, 15, 42, 209, 56, 253, 215, 76, 212, 132, 243, 51, 211, 194, 170, 223, 74, 216, 183, 141, 175, 42, 200, 109, 101, 60, 147, 36, 143, 155, 133, 184, 254,
        0, 47, 144, 204, 150, 14, 220, 51, 4, 169, 251, 189, 32, 221, 117, 157, 240, 78, 109, 16, 125, 255, 243, 36, 118, 252, 135, 191, 123, 122, 243, 229, 127, 1, 155, 141, 181, 96, 201, 75, 7, 32,
        199, 116, 181, 80, 151, 11, 11, 30, 130, 160}


    def "refuses to install distribution with #name zip and retry"() {
        given:
        _ * pathAssembler.getDistribution(configuration) >> localDistribution
        _ * localDistribution.distributionDir >> distributionDir
        _ * localDistribution.zipFile >> zipDestination

        when:
        install.createDist(configuration)

        then:
        def failure = thrown(RuntimeException)
        failure.message.matches(/[\S\s]*Downloaded distribution file .* is no valid zip file\.[\S\s]*/)

        and:
        3 * download.download(configuration.distribution, _) >> { createFile(it[1], content) }
        1 * download.download(distributionHashUri, _) >> { createFile(it[1], calcHash(content)) }
        0 * download._

        where:
        content                         | name
        BAD_ARCHIVE_CONTENT.getBytes()  | "bad archive"
        RANDOM_ARCHIVE_CONTENT          | "random content"
        new byte[0]                     | "empty file"
        getEvilTarData()                | "evil tar"
    }

    private calcHash(byte[] bytes) {
        sha256().hashBytes(bytes).toZeroPaddedString(sha256().hexDigits)
    }

    def "successfully get distribution hash"() {
        when:

        def hash = install.fetchDistributionSha256Sum(configuration, new TestFile(zipStore, zipFileName))

        then:

        hash == FETCHED_HASH

        and:
        1 * download.download(distributionHashUri, _) >> { createFile(it[1], FETCHED_HASH.getBytes()) }
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


    private createFile(File file, byte[] bytes) {
        file.parentFile.mkdirs()
        new FileOutputStream(file).withCloseable { FileOutputStream fos ->
            fos.write(bytes)
        }
    }

    static void createEvilZip(File zipDestination) {
        zipDestination.withOutputStream {
            new ZipOutputStream(it).withCloseable {  zos ->
                zos.putNextEntry(new ZipEntry('../../tmp/evil.sh'))
                zos.write("evil".getBytes('utf-8'))
                zos.closeEntry()
            }
        }
    }

    static byte[] getEvilTarData() {
        def outputStream = new ByteArrayOutputStream()
            new TarOutputStream(outputStream).withCloseable {  zos ->
                def bytes = "evil".getBytes('utf-8')
                def entry = new TarEntry('../../tmp/evil.sh')
                entry.size = bytes.length
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        return outputStream.toByteArray()
    }

}
