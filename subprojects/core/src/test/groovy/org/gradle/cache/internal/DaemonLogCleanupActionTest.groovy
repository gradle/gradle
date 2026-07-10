/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.internal.file.TestFiles
import org.gradle.cache.CleanupProgressMonitor
import org.gradle.launcher.daemon.logging.DaemonLogConstants
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.TimeUnit

import static org.gradle.cache.internal.DaemonLogCleanupAction.DEFAULT_RETENTION_DAYS

class DaemonLogCleanupActionTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def daemonBaseDir = temporaryFolder.createDir("daemon")
    def progressMonitor = Mock(CleanupProgressMonitor)
    def deleter = TestFiles.deleter()

    @Subject
    def cleanupAction = new DaemonLogCleanupAction(daemonBaseDir, deleter, { -> System.currentTimeMillis() - TimeUnit.DAYS.toMillis(DEFAULT_RETENTION_DAYS)})

    def "cleans up old daemon log files"() {
        given:
        def versionDir = createDaemonVersionDir("8.0")
        def oldLog1 = createDaemonLog(versionDir, 1234, fifteenDaysAgo())
        def oldLog2 = createDaemonLog(versionDir, 5678, twentyDaysAgo())
        def recentLog = createDaemonLog(versionDir, 9999, oneDayAgo())

        when:
        def cleanedUp = cleanupAction.execute(progressMonitor)

        then:
        cleanedUp
        2 * progressMonitor.incrementDeleted()
        1 * progressMonitor.incrementSkipped()
        oldLog1.assertDoesNotExist()
        oldLog2.assertDoesNotExist()
        recentLog.assertExists()
    }

    def "cleans up logs across multiple version directories"() {
        given:
        def version70Dir = createDaemonVersionDir("7.0")
        def version80Dir = createDaemonVersionDir("8.0")
        def version85Dir = createDaemonVersionDir("8.5")

        def oldLog70 = createDaemonLog(version70Dir, 1111, twentyDaysAgo())
        def oldLog80 = createDaemonLog(version80Dir, 2222, sixteenDaysAgo())
        def recentLog85 = createDaemonLog(version85Dir, 3333, oneDayAgo())

        when:
        def cleanedUp = cleanupAction.execute(progressMonitor)

        then:
        cleanedUp
        2 * progressMonitor.incrementDeleted()
        1 * progressMonitor.incrementSkipped()
        oldLog70.assertDoesNotExist()
        oldLog80.assertDoesNotExist()
        recentLog85.assertExists()
    }

    def "handles snapshot and milestone versions"() {
        given:
        def snapshotDir = createDaemonVersionDir("8.5-20231201000132+0000")
        def milestoneDir = createDaemonVersionDir("8.6-milestone-1")
        def rcDir = createDaemonVersionDir("8.7-rc-2")

        def oldSnapshotLog = createDaemonLog(snapshotDir, 1234, twentyDaysAgo())
        def oldMilestoneLog = createDaemonLog(milestoneDir, 5678, fifteenDaysAgo())
        def oldRcLog = createDaemonLog(rcDir, 9999, sixteenDaysAgo())

        when:
        def cleanedUp = cleanupAction.execute(progressMonitor)

        then:
        cleanedUp
        3 * progressMonitor.incrementDeleted()
        oldSnapshotLog.assertDoesNotExist()
        oldMilestoneLog.assertDoesNotExist()
        oldRcLog.assertDoesNotExist()
    }

    def "handles logs from commit builds"() {
        given:
        def commitDir = createDaemonVersionDir("8.12-commit-abc123def456")
        def oldCommitLog = createDaemonLog(commitDir, 7777, twentyDaysAgo())

        when:
        def cleanedUp = cleanupAction.execute(progressMonitor)

        then:
        cleanedUp
        1 * progressMonitor.incrementDeleted()
        oldCommitLog.assertDoesNotExist()
    }

    def "ignores non-log files in daemon directories"() {
        given:
        def versionDir = createDaemonVersionDir("8.0")
        def oldLog = createDaemonLog(versionDir, 1234, twentyDaysAgo())
        def registryFile = versionDir.file("registry.bin").createFile()
        registryFile.lastModified = twentyDaysAgo()
        def otherFile = versionDir.file("some-other-file.txt").createFile()
        otherFile.lastModified = twentyDaysAgo()

        when:
        def cleanedUp = cleanupAction.execute(progressMonitor)

        then:
        cleanedUp
        1 * progressMonitor.incrementDeleted()
        oldLog.assertDoesNotExist()
        registryFile.assertExists()
        otherFile.assertExists()
    }

    def "ignores directories that don't match version pattern"() {
        given:
        def invalidDir = daemonBaseDir.createDir("not-a-version")
        def logInInvalidDir = createDaemonLog(invalidDir, 1234, twentyDaysAgo())

        when:
        def cleanedUp = cleanupAction.execute(progressMonitor)

        then:
        cleanedUp
        0 * progressMonitor.incrementDeleted()
        logInInvalidDir.assertExists()
    }

    def "handles empty daemon base directory"() {
        given:
        def emptyDaemonDir = temporaryFolder.createDir("empty-daemon")
        def emptyCleanupAction = new DaemonLogCleanupAction(emptyDaemonDir, deleter, { -> System.currentTimeMillis() - TimeUnit.DAYS.toMillis(DEFAULT_RETENTION_DAYS)})

        when:
        def cleanedUp = emptyCleanupAction.execute(progressMonitor)

        then:
        cleanedUp
        0 * progressMonitor._
    }

    def "handles version directory without log files"() {
        given:
        def versionDir = createDaemonVersionDir("8.0")
        // No log files created

        when:
        def cleanedUp = cleanupAction.execute(progressMonitor)

        then:
        cleanedUp
        0 * progressMonitor._
        versionDir.assertExists()
    }

    def "display name includes daemon base directory"() {
        expect:
        cleanupAction.displayName == "Deleting old daemon log files in ${daemonBaseDir}"
    }

    def "respects exact retention boundary"() {
        given:
        def versionDir = createDaemonVersionDir("8.0")
        def exactlyFourteenDaysOld = createDaemonLog(versionDir, 1234, exactlyNDaysAgo(DEFAULT_RETENTION_DAYS))
        def justUnderFourteenDays = createDaemonLog(versionDir, 5678, exactlyNDaysAgo(DEFAULT_RETENTION_DAYS) + 1000) // 1 second newer

        when:
        def cleanedUp = cleanupAction.execute(progressMonitor)

        then:
        cleanedUp
        1 * progressMonitor.incrementDeleted()
        1 * progressMonitor.incrementSkipped()
        exactlyFourteenDaysOld.assertDoesNotExist()
        justUnderFourteenDays.assertExists()
    }

    private TestFile createDaemonVersionDir(String version) {
        return daemonBaseDir.createDir(version)
    }

    private TestFile createDaemonLog(TestFile versionDir, long pid, long lastModified) {
        def logFile = versionDir.file("${DaemonLogConstants.DAEMON_LOG_PREFIX}${pid}${DaemonLogConstants.DAEMON_LOG_SUFFIX}")
        logFile.text = "Daemon log content for PID ${pid}"
        logFile.lastModified = lastModified
        return logFile
    }

    private static long oneDayAgo() {
        exactlyNDaysAgo(1)
    }

    private static long fifteenDaysAgo() {
        exactlyNDaysAgo(15)
    }

    private static long sixteenDaysAgo() {
        exactlyNDaysAgo(16)
    }

    private static long twentyDaysAgo() {
        exactlyNDaysAgo(20)
    }

    private static long exactlyNDaysAgo(long days) {
        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days)
    }
}

