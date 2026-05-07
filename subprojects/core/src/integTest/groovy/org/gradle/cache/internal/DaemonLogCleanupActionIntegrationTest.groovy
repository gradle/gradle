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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.launcher.daemon.logging.DaemonLogConstants
import org.gradle.launcher.daemon.server.DaemonLogFile
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion

import java.util.concurrent.TimeUnit

class DaemonLogCleanupActionIntegrationTest extends AbstractIntegrationSpec implements GradleUserHomeCleanupFixture {

    def setup() {
        requireOwnGradleUserHomeDir("test modifies daemon logs")
        buildFile << """
            task dummy {
                doLast {
                    println "Build completed"
                }
            }
        """
    }

    def "cleans up old daemon log files during normal build"() {
        given:
        def versionDir = createDaemonVersionDir("8.0")
        def oldLog1 = createOldDaemonLog(versionDir, 1234)
        def oldLog2 = createOldDaemonLog(versionDir, 5678)
        def recentLog1 = createRecentDaemonLog(versionDir, 9999)
        def recentLog2 = createRecentDaemonLog(versionDir, 11111)

        when:
        succeeds("dummy")

        then:
        oldLog1.assertDoesNotExist()
        oldLog2.assertDoesNotExist()
        recentLog1.assertExists()
        recentLog2.assertExists()
    }

    def "doesn't clean up old daemon log files (15 days) when set to 16"() {
        withDaemonLogRetentionInDays(16)

        given:
        def versionDir = createDaemonVersionDir("8.0")
        def oldLog1 = createOldDaemonLog(versionDir, 1234)
        def recentLog1 = createRecentDaemonLog(versionDir, 9999)

        when:
        succeeds("dummy")

        then:
        oldLog1.assertExists()
        recentLog1.assertExists()
    }

    def "cleans up daemon logs across multiple version directories"() {
        given:
        def version70Dir = createDaemonVersionDir("7.0")
        def oldLog70 = createOldDaemonLog(version70Dir, 1234)
        def recentLog70 = createRecentDaemonLog(version70Dir, 5678)

        def version85Dir = createDaemonVersionDir("8.5")
        def oldLog85 = createOldDaemonLog(version85Dir, 2222)
        def recentLog85 = createRecentDaemonLog(version85Dir, 3333)

        def versionCommitDir = createDaemonVersionDir("8.12-commit-abc123def456")
        def oldLogCommit = createOldDaemonLog(versionCommitDir, 4444)

        def currentVersionDir = createDaemonVersionDir(GradleVersion.current().version)
        def recentLogCurrent = createRecentDaemonLog(currentVersionDir, 7777)
        def oldLogCurrent = createOldDaemonLog(currentVersionDir, 8888)

        when:
        succeeds("dummy")

        then:
        oldLog70.assertDoesNotExist()
        recentLog70.assertExists()

        oldLog85.assertDoesNotExist()
        recentLog85.assertExists()

        oldLogCommit.assertDoesNotExist()

        oldLogCurrent.assertDoesNotExist()
        recentLogCurrent.assertExists()
    }

    def "respects 14-day retention boundary"() {
        given:
        def versionDir = createDaemonVersionDir("8.0")
        def exactlyFourteenDaysOld = createDaemonLog(versionDir, 1234, exactlyFourteenDaysAgo())
        def justUnderFourteenDaysOld = createDaemonLog(versionDir, 5678, justUnderFourteenDays())
        def fifteenDaysOld = createDaemonLog(versionDir, 9999, fifteenDaysAgo())

        when:
        succeeds("dummy")

        then:
        exactlyFourteenDaysOld.assertDoesNotExist()
        justUnderFourteenDaysOld.assertExists()
        fifteenDaysOld.assertDoesNotExist()
    }

    def "does not clean up daemon logs when cleanup is disabled"() {
        given:
        disableCacheCleanupViaDsl()
        def versionDir = createDaemonVersionDir("8.0")
        def oldLog1 = createOldDaemonLog(versionDir, 1234)
        def oldLog2 = createOldDaemonLog(versionDir, 5678)
        def recentLog = createRecentDaemonLog(versionDir, 9999)

        when:
        succeeds("dummy")

        then:
        oldLog1.assertExists()
        oldLog2.assertExists()
        recentLog.assertExists()
    }

    def "only cleans up daemon logs once when default cleanup frequency is used"() {
        given:
        def versionDir = createDaemonVersionDir("8.0")
        def currentCacheDir = createVersionSpecificCacheDir(GradleVersion.current())

        and: "first build to establish gc.properties marker"
        succeeds("dummy")
        def gcFile = getGcFile(currentCacheDir)
        gcFile.assertExists()
        def beforeTimestamp = gcFile.lastModified()

        and: "create old logs after first build"
        def oldLog1 = createOldDaemonLog(versionDir, 1234)
        def oldLog2 = createOldDaemonLog(versionDir, 5678)

        when: "second build immediately after"
        succeeds("dummy")

        then: "cleanup is skipped due to frequency check"
        oldLog1.assertExists()
        oldLog2.assertExists()

        and: "gc.properties timestamp unchanged"
        gcFile.lastModified() == beforeTimestamp
    }

    def "always cleans up daemon logs when configured"() {
        given:
        alwaysCleanupCaches()
        def versionDir = createDaemonVersionDir("8.0")
        def currentCacheDir = createVersionSpecificCacheDir(GradleVersion.current())

        and: "first build to establish gc.properties marker"
        succeeds("dummy")
        def gcFile = getGcFile(currentCacheDir)
        gcFile.assertExists()
        def beforeTimestamp = gcFile.lastModified()

        and: "create old logs after first build"
        def oldLog1 = createOldDaemonLog(versionDir, 1234)
        def oldLog2 = createOldDaemonLog(versionDir, 5678)

        when: "second build immediately after"
        succeeds("dummy")

        then: "cleanup runs and deletes old logs"
        oldLog1.assertDoesNotExist()
        oldLog2.assertDoesNotExist()

        and: "gc.properties timestamp updated"
        gcFile.lastModified() > beforeTimestamp
    }

    // Helper methods

    TestFile getDaemonBaseDir() {
        gradleUserHomeDir.file(DaemonLogConstants.DAEMON_LOG_DIR)
    }

    TestFile createDaemonVersionDir(String version) {
        daemonBaseDir.file(version).createDir()
    }

    TestFile createDaemonLog(TestFile versionDir, long pid, long lastModifiedTime) {
        def logFile = versionDir.file(DaemonLogFile.getDaemonLogFileName(pid))
        logFile.text = "Daemon log content for PID ${pid}\n"
        logFile.lastModified = lastModifiedTime
        return logFile
    }

    TestFile createOldDaemonLog(TestFile versionDir, long pid) {
        createDaemonLog(versionDir, pid, fifteenDaysAgo())
    }

    TestFile createRecentDaemonLog(TestFile versionDir, long pid) {
        createDaemonLog(versionDir, pid, twoDaysAgo())
    }

    long fifteenDaysAgo() {
        daysOld(15)
    }

    private long daysOld(int days) {
        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days)
    }

    long exactlyFourteenDaysAgo() {
        daysOld(14)
    }

    long justUnderFourteenDays() {
        exactlyFourteenDaysAgo() + 10000 // 10 seconds newer
    }

    @Override
    TestFile getGradleUserHomeDir() {
        return executer.gradleUserHomeDir
    }
}
