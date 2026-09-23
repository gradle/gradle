/*
 * Copyright 2026 the original author or authors.
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
import org.gradle.cache.CleanupFrequency
import org.gradle.cache.CleanupProgressMonitor
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.util.concurrent.TimeUnit
import java.util.function.Supplier

class AgentOutputCleanupActionTest extends Specification {

    private static final int RETENTION_DAYS = 7

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def projectCacheDir = temporaryFolder.createDir(".gradle")
    def progressMonitor = Mock(CleanupProgressMonitor)

    def "deletes the output of invocations that are older than the retention period"() {
        given:
        def old = createInvocationDir("old", daysAgo(8))
        def older = createInvocationDir("older", daysAgo(30))
        def recent = createInvocationDir("recent", daysAgo(6))

        when:
        def cleanedUp = action().execute(progressMonitor)

        then:
        cleanedUp
        2 * progressMonitor.incrementDeleted()
        1 * progressMonitor.incrementSkipped()
        old.assertDoesNotExist()
        older.assertDoesNotExist()
        recent.assertExists()
    }

    def "keeps an old invocation directory whose output was modified recently"() {
        given:
        def invocationDir = createInvocationDir("long-running", daysAgo(8))
        invocationDir.file("build-output.log").lastModified = daysAgo(1)

        when:
        action().execute(progressMonitor)

        then:
        invocationDir.assertExists()
    }

    def "does nothing when agent mode has never been used"() {
        when:
        def cleanedUp = action().execute(progressMonitor)

        then:
        !cleanedUp
        !action().hasOutput()
        0 * progressMonitor._
        projectCacheDir.file("agent").assertDoesNotExist()
    }

    def "cleans up at most once a day"() {
        given:
        createInvocationDir("recent", daysAgo(1))

        when:
        def first = action().execute(progressMonitor)
        def old = createInvocationDir("old", daysAgo(8))
        def second = action().execute(progressMonitor)

        then:
        first
        !second
        old.assertExists()

        when:
        projectCacheDir.file(AgentOutputCleanupAction.GC_FILE_PATH).lastModified = daysAgo(2)
        def third = action().execute(progressMonitor)

        then:
        third
        old.assertDoesNotExist()
    }

    def "only deletes invocation directories"() {
        given:
        createInvocationDir("old", daysAgo(8))
        def strayFile = projectCacheDir.file("agent/builds/stray.txt").createFile()
        strayFile.lastModified = daysAgo(30)
        action().execute(progressMonitor)

        expect:
        strayFile.assertExists()
        projectCacheDir.file(AgentOutputCleanupAction.GC_FILE_PATH).assertExists()
        projectCacheDir.file("agent/builds").listFiles()*.name == ["stray.txt"]
    }

    private AgentOutputCleanupAction action() {
        new AgentOutputCleanupAction(projectCacheDir, { daysAgo(RETENTION_DAYS) } as Supplier<Long>, TestFiles.deleter(), CleanupFrequency.DAILY)
    }

    private TestFile createInvocationDir(String name, long lastModified) {
        def dir = projectCacheDir.createDir("agent/builds/$name")
        dir.file("build-output.log").createFile().lastModified = lastModified
        dir.lastModified = lastModified
        return dir
    }

    private static long daysAgo(int days) {
        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days)
    }
}
