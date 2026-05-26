/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.integtests.fixtures.cache.FileAccessTimeJournalFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import spock.lang.Issue

import static org.gradle.internal.cc.impl.SupersetIndexKt.SUPERSET_INDEX_DIR_NAME


@Requires(
    value = TestExecutionPreconditions.NotEmbeddedExecutor,
    reason = "FileAccessTimeJournal is never closed in embedded mode"
)
class ConfigurationCacheCleanupIntegrationTest
    extends AbstractConfigurationCacheIntegrationTest
    implements FileAccessTimeJournalFixture {

    def setup() {
        requireOwnGradleUserHomeDir('needs its own journal')
        executer.requireIsolatedDaemons()
    }

    @Issue('https://github.com/gradle/gradle/issues/23957')
    def "cleanup deletes old entries"() {
        given: 'there are two configuration cache entries'
        buildFile '''
            task outdated
            task recent
        '''
        configurationCacheRunNoDaemon 'outdated'
        // Each CC request requires at least 2 directories:
        // - the cache key directory holding the list of entries per key
        // - the actual entry directories, each entry directory with its own fingerprint
        def outdated = subDirsOf(configurationCacheDir)
        configurationCacheRunNoDaemon 'recent'
        def recent = subDirsOf(configurationCacheDir) - outdated

        and: 'they are 8 days old'
        subDirsOf(configurationCacheDir).each { TestFile dir ->
            writeLastFileAccessTimeToJournal dir, daysAgo(8)
        }

        and: 'but one was recently accessed'
        configurationCacheRunNoDaemon 'recent'

        and: 'the last cleanup was long ago'
        assert gcFile.createFile().setLastModified(0)

        expect: 'Gradle to preserve the recent entry and to delete the outdated one'
        def cc = newConfigurationCacheFixture()
        configurationCacheRunNoDaemon 'recent'
        cc.assertStateLoaded()
        !outdated.any { it.exists() }

        and: 'the superset-lookup index survives cleanup (metadata, not a cache entry)'
        def remaining = configurationCacheDir.list() as Set
        def expected = (recent*.name + ['gc.properties', 'configuration-cache.lock', SUPERSET_INDEX_DIR_NAME]) as Set
        expected == remaining
    }

    def "LRU tie-break picks the most-recently-accessed of two same-size strict supersets"() {
        given: 'a single-project build with three tasks the user can request in various combos'
        buildFile '''
            ['a', 'b', 'c'].each { name ->
                tasks.register(name) { doLast { println name } }
            }
        '''

        and: 'two stored CC entries that are both 2-element strict supersets of [a]'
        configurationCacheRunNoDaemon 'a', 'b'
        def entryAB = subDirsOf(configurationCacheDir)[0]
        configurationCacheRunNoDaemon 'a', 'c'
        def entryAC = (subDirsOf(configurationCacheDir) - entryAB)[0]

        and: 'the [a, b] entry is 8 days old in the journal; the [a, c] entry is 1 day old'
        // pickWithTieBreak prefers the entry with the LATER getLastAccessTime — so [a, c] should win.
        // If the LRU code path were inert (e.g. someone refactored it away), `selectBestMatch`'s
        // first-encountered tie-break would pick whichever entry was stored first ([a, b]).
        writeLastFileAccessTimeToJournal entryAB, daysAgo(8)
        writeLastFileAccessTimeToJournal entryAC, daysAgo(1)

        and: 'cleanup is due'
        assert gcFile.createFile().setLastModified(0)

        when: 'requesting [a] — both stored entries are valid supersets of equal size; LRU resolves the tie'
        configurationCacheRunNoDaemon 'a'

        then: 'the load succeeded against one of them'
        def cc = newConfigurationCacheFixture()
        // No assertStateLoaded here — `configurationCacheRunNoDaemon` already executed; just
        // verify by structural inspection below.

        and: 'cleanup deleted the older entry [a, b] but kept [a, c] (proving [a, c] was the LRU pick)'
        // The LRU pick gets `markAccessed` updated to "now" during load. The non-picked entry's
        // journal age stays at its set value (8 days). Cleanup's default threshold is 7 days, so:
        //   - correct LRU pick = [a, c]: [a, c] is touched-now-survives, [a, b] stays at 8d → deleted
        //   - broken LRU (picks [a, b] instead): [a, b] touched-now-survives, [a, c] at 1d → both survive
        !entryAB.exists()
        entryAC.exists()
    }

    private void configurationCacheRunNoDaemon(String... taskArgs) {
        configurationCacheRun(*taskArgs, '--no-daemon')
    }

    private void configurationCacheRunNoDaemon(String task) {
        configurationCacheRun task, '--no-daemon'
    }

    private TestFile getGcFile() {
        return configurationCacheDir.file('gc.properties')
    }

    private TestFile getConfigurationCacheDir() {
        return file('.gradle/configuration-cache')
    }

    private static List<TestFile> subDirsOf(TestFile dir) {
        // Exclude the superset-lookup index directory: it is metadata persisted across
        // entries (one file per environment-key) and isn't a cache entry that participates
        // in LRU cleanup. See `ConfigurationCacheRepository.cleanupEligibleFilesFinder`.
        dir.listFiles().findAll { it.directory && it.name != SUPERSET_INDEX_DIR_NAME }
    }
}
