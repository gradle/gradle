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

package org.gradle.kotlin.dsl

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.cache.FileAccessTimeJournalFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion

import java.util.concurrent.TimeUnit

import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.SECONDS
import static org.gradle.api.internal.cache.CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES

class KotlinScriptCacheCleanupIntegrationTest
    extends AbstractIntegrationSpec
    implements FileAccessTimeJournalFixture {

    @UnsupportedWithConfigurationCache(because = "tests script compilation")
    def "cleanup deletes old script cache entries"() {
        given:
        // needs to stop daemon and have its own journal
        executer.requireIsolatedDaemons()
        requireOwnGradleUserHomeDir("needs its own journal")

        and: 'seed script cache to have a baseline to compare against'
        buildKotlinFile.text = """
            tasks.register("run") {
                doLast { println("ok") }
            }
        """
        run 'run'

        and:
        String[] scriptCacheBaseLine = scriptCacheDir.list()
        TestFile outdatedScriptCache = scriptCacheDir.file('7c8e05b2aa9d61f6b8422a683803c455').tap {
            assert !exists()
            file('classes/Program.class').createFile()
        }
        TestFile gcFile = scriptCacheDir.file('.internal/gc.properties')
        gcFile.createFile().lastModified = daysAgo(2)
        writeJournalInceptionTimestamp(daysAgo(8))
        // mark the outdated script cache as last accessed older than retention so it is a candidate for cleanup
        writeLastFileAccessTimeToJournal(outdatedScriptCache, daysAgo(DEFAULT_MAX_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES + 1))

        when: 'trigger first cleanup pass (soft delete)'
        run '--stop' // ensure daemon does not cache file access times in memory
        def beforeSoftCleanup = MILLISECONDS.toSeconds(System.currentTimeMillis())
        // start as new process so journal is not restored from in-memory cache
        run "run"

        then: 'outdated entry is soft-deleted (kept with markers) and baseline entries are still present'
        outdatedScriptCache.exists()
        isSoftDeleted(outdatedScriptCache.name)
        locksAndSoftDeletionFilesExist(outdatedScriptCache.name)
        gcFile.lastModified() >= SECONDS.toMillis(beforeSoftCleanup)

        when: 'simulate passage of time beyond soft deletion window and trigger cleanup again (hard delete)'
        def beforeHardCleanup = MILLISECONDS.toSeconds(System.currentTimeMillis())
        def sevenHoursAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(7)
        setSoftDeletedTime(outdatedScriptCache.name, sevenHoursAgo)
        gcFile.lastModified = daysAgo(2)
        run "run"

        then: 'outdated entry directory is hard deleted and markers removed; baseline remains'
        outdatedScriptCache.assertDoesNotExist()
        locksAndSoftDeletionFilesAreDeleted(outdatedScriptCache.name)
        // Baseline entries are still the same
        scriptCacheDir.list() as Set == scriptCacheBaseLine as Set
        gcFile.lastModified() >= SECONDS.toMillis(beforeHardCleanup)
    }

    boolean isSoftDeleted(String key) {
        return new TestFile(scriptCacheDir, ".internal/gc/${key}/soft.deleted").exists()
            && new TestFile(scriptCacheDir, ".internal/gc/${key}/gc.properties").exists()
    }

    void setSoftDeletedTime(String key, long millis) {
        def keyGcDir = new TestFile(scriptCacheDir, ".internal/gc/${key}")
        def softDeleted = keyGcDir.file("soft.deleted")
        def softGc = keyGcDir.file("gc.properties")
        softDeleted.lastModified = millis
        softGc.lastModified = millis
    }

    boolean locksAndSoftDeletionFilesExist(String key) {
        return new TestFile(scriptCacheDir, ".internal/locks/${key}.lock").exists()
            && new TestFile(scriptCacheDir, ".internal/gc/${key}").exists()
    }

    boolean locksAndSoftDeletionFilesAreDeleted(String key) {
        return !new TestFile(scriptCacheDir, ".internal/locks/${key}.lock").exists()
            && !new TestFile(scriptCacheDir, ".internal/gc/${key}").exists()
    }

    TestFile getScriptCacheDir() {
        return userHomeCacheDir.file(GradleVersion.current().version).file('kotlin-dsl').file('scripts')
    }
}
