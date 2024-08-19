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
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

class ConfigurationCacheCleanupIntegrationTest
    extends AbstractConfigurationCacheIntegrationTest
    implements FileAccessTimeJournalFixture {

    @Issue('https://github.com/gradle/gradle/issues/23957')
    def "cleanup deletes old entries"() {
        given: 'there are two configuration cache entries'
        executer.requireIsolatedDaemons()
        buildFile '''
            task outdated
            task recent
        '''
        configurationCacheRunAndStop 'outdated'
        TestFile outdated = single(subDirsOf(cacheDir))
        configurationCacheRunAndStop 'recent'
        TestFile recent = single(subDirsOf(cacheDir) - outdated)

        and: 'they are 15 days old'
        file('.gradle/configuration-cache').listFiles().findAll { it.directory }.each { TestFile dir ->
            writeLastFileAccessTimeToJournal dir, daysAgo(15)
        }

        and: 'but one was recently accessed'
        configurationCacheRunAndStop 'recent'

        and: 'the last cleanup was 8 days ago'
        writeJournalInceptionTimestamp daysAgo(8)
        gcFile.createFile().lastModified = daysAgo(8)

        expect: 'Gradle to preserve the recent entry and to delete the outdated one'
        boolean recentEntryIsReused = true
        def cc = newConfigurationCacheFixture()
        ConcurrentTestUtil.poll(60, 0, 10) {
            configurationCacheRun 'recent'
            recentEntryIsReused &= cc.reused
            run '--stop'
            assert !outdated.isDirectory()
        }
        recentEntryIsReused

        and:
        def remaining = cacheDir.listFiles().collect { it.name } as Set
        def expected = [recent.name, 'gc.properties', 'configuration-cache.lock'] as Set
        expected == remaining
    }

    private void configurationCacheRunAndStop(String task) {
        configurationCacheRun task
        run '--stop'
    }

    private static List<TestFile> subDirsOf(TestFile dir) {
        dir.listFiles().findAll { it.directory }
    }

    private TestFile getGcFile() {
        return cacheDir.file("gc.properties")
    }

    private TestFile getCacheDir() {
        return file(".gradle/configuration-cache")
    }

    private static <T> T single(List<T> list) {
        list.with {
            assert size() == 1
            get(0)
        }
    }
}
