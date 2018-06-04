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

package org.gradle.api.internal.changedetection.state

import org.gradle.internal.resource.local.FileAccessTimeJournal
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.InMemoryCacheFactory
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class IndexedCacheBackedFileAccessTimeJournalTest extends Specification {

    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def persistentCache = new InMemoryCacheFactory().open(tmpDir.createDir("cacheBaseDir"), "cache")
    def indexedCache = persistentCache.createCache(IndexedCacheBackedFileAccessTimeJournal.baseCacheParameters())

    @Subject FileAccessTimeJournal journal = new IndexedCacheBackedFileAccessTimeJournal(indexedCache, indexedCache)

    def file = tmpDir.createFile("a/1.txt")

    def "reads previously written value"() {
        when:
        journal.setLastAccessTime(file, 23)

        then:
        journal.getLastAccessTime(file) == 23
    }

    def "overwrites existing value"() {
        when:
        journal.setLastAccessTime(file, 23)
        journal.setLastAccessTime(file, 42)

        then:
        journal.getLastAccessTime(file) == 42
    }

    def "falls back to modification time when no value was written previously"() {
        expect:
        journal.getLastAccessTime(file) == file.lastModified()

        when:
        file.makeOlder()

        then:
        journal.getLastAccessTime(file) == file.lastModified()
    }
}
