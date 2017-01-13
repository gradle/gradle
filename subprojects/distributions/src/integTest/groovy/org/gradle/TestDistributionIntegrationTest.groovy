/*
 * Copyright 2016 the original author or authors.
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

package org.gradle

import com.google.common.collect.Iterables
import org.gradle.test.fixtures.file.TestFile

import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class TestDistributionIntegrationTest extends  DistributionIntegrationSpec {
    @Override
    String getDistributionLabel() {
        return "test-bin"
    }

    def testBinZipContents() {
        given:
        TestFile contentsDir = unpackDistribution(distributionLabel, temporaryFolder.file('bin-dist'))
        TestFile binDistcontentsDir = unpackDistribution("bin", temporaryFolder.file('test-bin-dist'))
        def testDistContents = contentsDir.allDescendants()
        def binDistContents = binDistcontentsDir.allDescendants()

        expect:
        testDistContents == binDistContents

        assertAllFilesExceptVersionInfoAreTheSame()

        def versionInfos = testDistContents.findAll { it.contains 'version-info' }
        def versionInfoName = Iterables.getOnlyElement(versionInfos)
        def testVersionInfo = contentsDir.file(versionInfoName)
        def versionInfo = binDistcontentsDir.file(versionInfoName)
        checkVersionInfoContents(testVersionInfo)
        checkVersionInfoContents(versionInfo)
        assertSameClasspathManifests(testVersionInfo, versionInfo)
        assertSameBaseVersion(testVersionInfo, versionInfo)
    }

    private void assertAllFilesExceptVersionInfoAreTheSame() {
        def testEntries = fromZipFile(zip) { ZipFile file ->
            file.entries().toList()
        }
        def entries = fromZipFile(getZip("bin")) { ZipFile file ->
            file.entries().toList()
        }

        testEntries.findAll { !it.name.contains('version-info') } each { ZipEntry testEntry ->
            ZipEntry entry = entries.find {
                def entryPath = Paths.get(it.name)
                def testEntryPath = Paths.get(testEntry.name)
                if (entryPath.nameCount > 1 && testEntryPath.nameCount > 1) {
                    entryPath.subpath(1, entryPath.nameCount) == testEntryPath.subpath(1, testEntryPath.nameCount)
                } else {
                    entryPath.nameCount == testEntryPath.nameCount
                }
            }
            assert entry
            assert testEntry.isDirectory() == entry.isDirectory()
            assert testEntry.crc == entry.crc
        }
    }

    private static void checkVersionInfoContents(File versionInfo) {
        new ZipFile(versionInfo).withCloseable { zipFile ->
            for (ZipEntry entry : zipFile.entries()) {
                assert ['META-INF/', 'META-INF/MANIFEST.MF', 'org/', 'org/gradle/', 'org/gradle/build-receipt.properties', 'gradle-version-info-classpath.properties'].contains(entry.name)
            }
        }
    }

    private static void assertSameClasspathManifests(TestFile testVersionInfo, TestFile versionInfo) {
        def testManifest = versionInfoManifest(testVersionInfo)
        def manifest = versionInfoManifest(versionInfo)
        assert testManifest == manifest
    }

    private static void assertSameBaseVersion(TestFile testVersionInfo, TestFile versionInfo) {
        Properties testBuildReceipt = buildReceipt(testVersionInfo)
        Properties buildReceipt = buildReceipt(versionInfo)
        assert testBuildReceipt.baseVersion == buildReceipt.baseVersion
    }

    private static byte[] versionInfoManifest(File zipFile) {
        fromZipFile(zipFile) { ZipFile zip ->
            inputStream(zip, 'gradle-version-info-classpath.properties').bytes
        }
    }

    private static Properties buildReceipt(File zipFile) {
        fromZipFile(zipFile) { ZipFile zip ->
            def receipt = new Properties()
            inputStream(zip, 'org/gradle/build-receipt.properties').withStream { InputStream stream ->
                receipt.load(stream)
            }
            receipt
        }
    }

    private static <T> T fromZipFile(File zipFile, Closure<T> extraction) {
        new ZipFile(zipFile).withCloseable(extraction)
    }
    private static InputStream inputStream(ZipFile file, String name) {
        file.getInputStream(file.getEntry(name))
    }
}
