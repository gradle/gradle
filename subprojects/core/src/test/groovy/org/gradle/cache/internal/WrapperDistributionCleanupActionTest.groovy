/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.cache.internal

import org.gradle.cache.CleanupProgressMonitor
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.gradle.util.JarUtils
import org.gradle.util.internal.DefaultGradleVersion
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class WrapperDistributionCleanupActionTest extends Specification implements GradleUserHomeCleanupFixture {

    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def userHomeDir = temporaryFolder.createDir("user-home")
    def usedGradleVersions = Mock(UsedGradleVersions) {
        getUsedGradleVersions() >> ([] as SortedSet)
    }
    def progressMonitor = Mock(CleanupProgressMonitor)

    @Subject def cleanupAction = new WrapperDistributionCleanupAction(userHomeDir, usedGradleVersions)

    def "deletes distributions for unused versions"() {
        given:
        def unusedDist = createDistributionChecksumDir(GradleVersion.version("2.3.4"))
        def usedVersion = GradleVersion.version("3.4.5")
        def stillUsedDist = createDistributionChecksumDir(usedVersion)
        def currentDist = createDistributionChecksumDir(currentVersion)
        def unusedFutureDist = createDistributionChecksumDir(currentVersion.nextMajorVersion)

        when:
        cleanupAction.execute(progressMonitor)

        then:
        1 * usedGradleVersions.getUsedGradleVersions() >> ([usedVersion] as SortedSet)
        3 * progressMonitor.incrementSkipped(1)
        1 * progressMonitor.incrementDeleted()
        unusedDist.parentFile.assertDoesNotExist()
        stillUsedDist.assertExists()
        currentDist.assertExists()
        unusedFutureDist.assertExists()
    }

    def "deletes custom distributions for unused versions"() {
        given:
        def versionToCleanUp = GradleVersion.version("2.3.4")
        def oldAllDist = createCustomDistributionChecksumDir("my-all-dist-${versionToCleanUp.version}", versionToCleanUp)
        def oldBinDist = createCustomDistributionChecksumDir("my-bin-dist-${versionToCleanUp.version}", versionToCleanUp)
        def currentAllDist = createCustomDistributionChecksumDir("my-all-dist-${currentVersion.version}", currentVersion)
        def currentBinDist = createCustomDistributionChecksumDir("my-bin-dist-${currentVersion.version}", currentVersion)

        when:
        cleanupAction.execute(progressMonitor)

        then:
        1 * progressMonitor.incrementSkipped(2)
        2 * progressMonitor.incrementDeleted()
        oldAllDist.parentFile.assertDoesNotExist()
        oldBinDist.parentFile.assertDoesNotExist()
        currentAllDist.assertExists()
        currentBinDist.assertExists()
    }

    def "does not delete custom parent directories that are still in use"() {
        given:
        def versionToCleanUp = GradleVersion.version("2.3.4")
        def oldDist = createCustomDistributionChecksumDir("my-dist", versionToCleanUp)
        def currentDist = createCustomDistributionChecksumDir("my-dist", currentVersion)

        when:
        cleanupAction.execute(progressMonitor)

        then:
        1 * progressMonitor.incrementSkipped(1)
        1 * progressMonitor.incrementDeleted()
        oldDist.assertDoesNotExist()
        currentDist.assertExists()
    }

    def "ignores custom distributions with multiple sub directories"() {
        given:
        def versionToCleanUp = GradleVersion.version("2.3.4")
        def distributionWithMultipleSubDirectories = createCustomDistributionChecksumDir("my-dist", versionToCleanUp)
        distributionWithMultipleSubDirectories.createDir("foo")

        when:
        cleanupAction.execute(progressMonitor)

        then:
        0 * progressMonitor._
        distributionWithMultipleSubDirectories.assertExists()
    }

    def "ignores custom distributions without gradle-base-services JAR"() {
        given:
        def versionToCleanUp = GradleVersion.version("2.3.4")
        def distributionWithoutJar = createCustomDistributionChecksumDir("my-dist", versionToCleanUp, DEFAULT_JAR_PREFIX, System.currentTimeMillis(), { version, jarFile -> })

        when:
        cleanupAction.execute(progressMonitor)

        then:
        0 * progressMonitor._
        distributionWithoutJar.assertExists()
    }

    def "ignores custom distributions without build receipt in gradle-base-services JAR"() {
        given:
        def versionToCleanUp = GradleVersion.version("2.3.4")
        def distributionWithoutBuildReceiptInJar = createCustomDistributionChecksumDir("my-dist", versionToCleanUp, DEFAULT_JAR_PREFIX, System.currentTimeMillis()) { version, jarFile ->
            jarFile << JarUtils.jarWithContents(foo: "bar")
        }

        when:
        cleanupAction.execute(progressMonitor)

        then:
        0 * progressMonitor._
        distributionWithoutBuildReceiptInJar.assertExists()
    }

    def "ignores distributions with missing gradle version"() {
        given:
        def versionToCleanUp = GradleVersion.version("2.3.4")
        def distributionWithMissingVersion = createCustomDistributionChecksumDir("gradle-1-invalid-all", versionToCleanUp, DEFAULT_JAR_PREFIX, System.currentTimeMillis(), { version, jarFile -> })

        when:
        cleanupAction.execute(progressMonitor)

        then:
        0 * progressMonitor._
        distributionWithMissingVersion.assertExists()
    }

    def "ignores distributions with invalid gradle version"() {
        given:
        def versionToCleanUp = GradleVersion.version("2.3.4")
        def distributionWithInvalidVersion = createCustomDistributionChecksumDir("gradle-1-invalid-all", versionToCleanUp, DEFAULT_JAR_PREFIX, System.currentTimeMillis()) { version, jarFile ->
            jarFile << JarUtils.jarWithContents((DefaultGradleVersion.RESOURCE_NAME.substring(1)): "${DefaultGradleVersion.VERSION_NUMBER_PROPERTY}: foo")
        }

        when:
        cleanupAction.execute(progressMonitor)

        then:
        0 * progressMonitor._
        distributionWithInvalidVersion.assertExists()
    }

    def "ignores distributions with unreadable JAR files"() {
        given:
        def versionToCleanUp = GradleVersion.version("2.3.4")
        def distributionWithUnreadableJarFile = createCustomDistributionChecksumDir("gradle-1-invalid-all", versionToCleanUp)
        def distDir = distributionWithUnreadableJarFile.listFiles()[0]
        def libDir = distDir.listFiles()[0]
        def jarFile = libDir.listFiles()[0]
        jarFile.text = "not a JAR"

        when:
        cleanupAction.execute(progressMonitor)

        then:
        0 * progressMonitor._
        distributionWithUnreadableJarFile.assertExists()
    }

    def "checks for gradle-core-*.jar"() {
        given:
        def version = GradleVersion.version("1.0")
        def distribution = createDistributionChecksumDir(version, "gradle-core")

        when:
        cleanupAction.execute(progressMonitor)

        then:
        1 * progressMonitor.incrementDeleted()
        0 * progressMonitor._
        distribution.assertDoesNotExist()
    }

    def "checks for gradle-version-info-*.jar"() {
        given:
        def version = GradleVersion.version("3.0")
        def distribution = createDistributionChecksumDir(version, "gradle-version-info")

        when:
        cleanupAction.execute(progressMonitor)

        then:
        1 * progressMonitor.incrementDeleted()
        0 * progressMonitor._
        distribution.assertDoesNotExist()
    }

    def "does not clean up if no version is found"() {
        given:
        def version = GradleVersion.version("3.0")
        def distribution = createDistributionChecksumDir(version, "some-other-jar")

        when:
        cleanupAction.execute(progressMonitor)

        then:
        0 * progressMonitor._
        distribution.assertExists()
    }

    def "does not delete recently downloaded but unused distributions"() {
        given:
        def justDownloadedButUnusedDist = createDistributionChecksumDir(GradleVersion.version("2.3.4"), DEFAULT_JAR_PREFIX, System.currentTimeMillis())

        when:
        cleanupAction.execute(progressMonitor)

        then:
        1 * progressMonitor.incrementSkipped()
        justDownloadedButUnusedDist.assertExists()
    }

    @Override
    TestFile getGradleUserHomeDir() {
        return userHomeDir
    }
}
