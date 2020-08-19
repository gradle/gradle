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

package org.gradle.configurationcache

import org.gradle.integtests.fixtures.cache.FileAccessTimeJournalFixture
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Ignore


class ConfigurationCacheCleanupIntegrationTest
    extends AbstractConfigurationCacheIntegrationTest
    implements FileAccessTimeJournalFixture {

    @Ignore('https://github.com/gradle/gradle-private/issues/3121')
    def "cleanup deletes old entries"() {
        given:
        executer.requireIsolatedDaemons()
        writeJournalInceptionTimestamp(daysAgo(8))
        gcFile.createFile().lastModified = daysAgo(8)

        and:
        def outdated = createCacheEntryDir("outdated")
        writeLastFileAccessTimeToJournal(outdated, daysAgo(15))

        expect:
        ConcurrentTestUtil.poll(60, 0, 10) {
            configurationCacheRun 'help'
            run '--stop'
            assert !outdated.isDirectory()
        }

        and:
        cacheDir.listFiles().length == 3 // gc file + cache properties + 'help' state
    }

    private TestFile createCacheEntryDir(String entry) {
        TestFile dir = cacheDir.createDir(entry)
        dir.createFile("state.bin")
        dir.createFile("fingerprint.bin")
        return dir
    }

    private TestFile getGcFile() {
        return cacheDir.file("gc.properties")
    }

    private TestFile getCacheDir() {
        return file(".gradle/configuration-cache")
    }
}
