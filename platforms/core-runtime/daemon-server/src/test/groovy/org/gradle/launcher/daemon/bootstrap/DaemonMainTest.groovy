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

package org.gradle.launcher.daemon.bootstrap

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant

import static java.nio.file.Files.setLastModifiedTime
import static java.time.Instant.now
import static java.time.temporal.ChronoUnit.DAYS
import static org.gradle.launcher.daemon.bootstrap.DaemonMain.cleanupOldLogFiles
import static org.gradle.launcher.daemon.server.DaemonLogFile.DAEMON_LOG_PREFIX
import static org.gradle.launcher.daemon.server.DaemonLogFile.DAEMON_LOG_SUFFIX

class DaemonMainTest extends Specification {

    @TempDir
    Path tempDir

    @Unroll
    def "deletes old log files older than 14 days for version #versionDir"() {
        given:
        def versionDirectory = createVersionDir(versionDir)
        def currentLog = createLogFileInDir(versionDirectory, "current", now())
        def oldLog = createLogFileInDir(versionDirectory, "old", getDaysAgo(15))

        when:
        cleanupOldLogFiles(tempDir.toFile())

        then:
        currentLog.exists()
        !oldLog.exists()

        where:
        versionDir << [
            "8.12",                                    // Standard release
            "9.0",                                     // Major version
            "8.12.1",                                  // Patch release
            "9.0-milestone-3",                         // Another milestone
            "8.13-rc-2",                               // Another RC
            "9.1-SNAPSHOT",                            // Snapshot
            "9.0-20241120120000+0000",                 // Build with timestamp
            "8.12-milestone-1-20241115100000+0000",    // Milestone with timestamp
            "8.13-rc-1-SNAPSHOT"                       // RC snapshot
        ]
    }

    def "keeps recent log files younger than 14 days"() {
        given:
        def versionDir = createVersionDir("8.12")
        def currentLog = createLogFileInDir(versionDir, "current", now())
        def recentLog = createLogFileInDir(versionDir, "recent", getDaysAgo(7))

        when:
        cleanupOldLogFiles(tempDir.toFile())

        then:
        currentLog.exists()
        recentLog.exists()
    }

    def "only deletes files with correct prefix"() {
        given:
        def versionDir = createVersionDir("8.12")
        def currentLog = createLogFileInDir(versionDir, "123", now())
        def wrongPrefix = createFileInDir(versionDir, "other-456" + DAEMON_LOG_SUFFIX, getDaysAgo())

        when:
        cleanupOldLogFiles(tempDir.toFile())

        then:
        currentLog.exists()
        wrongPrefix.exists()
    }

    def "only deletes files with correct suffix"() {
        given:
        def versionDir = createVersionDir("8.12")
        def currentLog = createLogFileInDir(versionDir, "123", now())
        def wrongSuffix = createFileInDir(versionDir, DAEMON_LOG_PREFIX + "456.txt", getDaysAgo())

        when:
        cleanupOldLogFiles(tempDir.toFile())

        then:
        currentLog.exists()
        wrongSuffix.exists()
    }

    Instant getDaysAgo(int days = 20) {
        now().minus(days, DAYS)
    }

    def "handles non-existent parent directory gracefully"() {
        given:
        def nonExistentParent = new File("/nonexistent/path/daemon-base")

        when:
        cleanupOldLogFiles(nonExistentParent)

        then:
        noExceptionThrown()
    }

    def "ignores directories"() {
        given:
        def versionDir = createVersionDir("8.12")
        def currentLog = createLogFileInDir(versionDir, "current", now())
        def oldDir = versionDir.toPath().resolve(DAEMON_LOG_PREFIX + "old" + DAEMON_LOG_SUFFIX).toFile()
        oldDir.mkdir()
        setLastModifiedTime(oldDir.toPath(), FileTime.from(getDaysAgo()))

        when:
        cleanupOldLogFiles(tempDir.toFile())

        then:
        currentLog.exists()
        oldDir.exists()
    }

    def "deletes multiple old log files"() {
        given:
        def versionDir = createVersionDir("8.12")
        def currentLog = createLogFileInDir(versionDir, "current", now())
        def oldLog1 = createLogFileInDir(versionDir, "old1", getDaysAgo(15))
        def oldLog2 = createLogFileInDir(versionDir, "old2", getDaysAgo())
        def oldLog3 = createLogFileInDir(versionDir, "old3", getDaysAgo(30))

        when:
        cleanupOldLogFiles(tempDir.toFile())

        then:
        currentLog.exists()
        !oldLog1.exists()
        !oldLog2.exists()
        !oldLog3.exists()
    }

    def "processes multiple version directories"() {
        given:
        def versionDir812 = createVersionDir("8.12")
        def versionDir90 = createVersionDir("9.0")
        def currentLog812 = createLogFileInDir(versionDir812, "current", now())
        def oldLog812 = createLogFileInDir(versionDir812, "old", getDaysAgo(15))
        def currentLog90 = createLogFileInDir(versionDir90, "current", now())
        def oldLog90 = createLogFileInDir(versionDir90, "old", getDaysAgo())

        when:
        cleanupOldLogFiles(tempDir.toFile())

        then:
        currentLog812.exists()
        !oldLog812.exists()
        currentLog90.exists()
        !oldLog90.exists()
    }

    def "ignores non-version directories"() {
        given:
        def versionDir = createVersionDir("8.12")
        def nonVersionDir = tempDir.resolve("not-a-version").toFile()
        nonVersionDir.mkdirs()
        def validLog = createLogFileInDir(versionDir, "valid", now())
        def oldValidLog = createLogFileInDir(versionDir, "old-valid", getDaysAgo(15))
        def ignoredLog = createFileInDir(nonVersionDir, DAEMON_LOG_PREFIX + "ignored" + DAEMON_LOG_SUFFIX, getDaysAgo())

        when:
        cleanupOldLogFiles(tempDir.toFile())

        then:
        validLog.exists()
        !oldValidLog.exists()
        ignoredLog.exists()
    }

    private File createVersionDir(String version) {
        def dir = tempDir.resolve(version).toFile()
        dir.mkdirs()
        return dir
    }

    private File createLogFileInDir(File dir, String name, Instant lastModified) {
        def fileName = DAEMON_LOG_PREFIX + name + DAEMON_LOG_SUFFIX
        createFileInDir(dir, fileName, lastModified)
    }

    private File createFileInDir(File dir, String name, Instant lastModified) {
        def file = dir.toPath().resolve(name)
        Files.createFile(file)
        setLastModifiedTime(file, FileTime.from(lastModified))
        file.toFile()
    }

}
