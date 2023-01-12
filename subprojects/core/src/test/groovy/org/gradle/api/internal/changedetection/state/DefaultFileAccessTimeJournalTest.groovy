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

import org.gradle.cache.CacheDecorator
import org.gradle.cache.internal.DefaultInMemoryCacheDecoratorFactory
import org.gradle.cache.internal.DefaultUnscopedCacheBuilderFactory
import org.gradle.cache.internal.scopes.DefaultCacheScopeMapping
import org.gradle.cache.internal.scopes.DefaultGlobalScopedCacheBuilderFactory
import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.TestInMemoryCacheFactory
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.api.internal.changedetection.state.DefaultFileAccessTimeJournal.CACHE_KEY
import static org.gradle.api.internal.changedetection.state.DefaultFileAccessTimeJournal.FILE_ACCESS_PROPERTIES_FILE_NAME
import static org.gradle.api.internal.changedetection.state.DefaultFileAccessTimeJournal.INCEPTION_TIMESTAMP_KEY
import static org.gradle.util.internal.GUtil.loadProperties

class DefaultFileAccessTimeJournalTest extends Specification {

    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def cachesDir = tmpDir.createDir("caches")
    def cacheScopeMapping = new DefaultCacheScopeMapping(cachesDir, GradleVersion.current())
    def cacheRepository = new DefaultUnscopedCacheBuilderFactory(cacheScopeMapping, new TestInMemoryCacheFactory())
    def globalScopedCache = new DefaultGlobalScopedCacheBuilderFactory(cachesDir, cacheRepository)
    def cacheDecoratorFactory = Stub(DefaultInMemoryCacheDecoratorFactory) {
        decorator(_, _) >> Stub(CacheDecorator) {
            decorate(_, _, _, _, _) >> { cacheId, cacheName, persistentCache, crossProcessCacheAccess, asyncCacheAccess ->
                persistentCache
            }
        }
    }

    @Subject FileAccessTimeJournal journal = new DefaultFileAccessTimeJournal(globalScopedCache, cacheDecoratorFactory)

    def file = tmpDir.createFile("a/1.txt").makeOlder()

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

    def "deletes last access time when asked to do so"() {
        given:
        def inceptionTimestamp = loadInceptionTimestamp()

        when:
        journal.setLastAccessTime(file, 42)
        journal.deleteLastAccessTime(file)

        then:
        journal.getLastAccessTime(file) == inceptionTimestamp
    }

    def "loads and uses previously stored inception time unless file has a later modification time"() {
        given:
        def inceptionTimestamp = System.currentTimeMillis() - 30_000
        file.lastModified = inceptionTimestamp - 30_000

        when:
        journal.stop()
        writeInceptionTimestamp(inceptionTimestamp)
        journal = new DefaultFileAccessTimeJournal(globalScopedCache, cacheDecoratorFactory)

        then:
        journal.getLastAccessTime(file) == inceptionTimestamp

        when:
        file.lastModified = System.currentTimeMillis()

        then:
        journal.getLastAccessTime(file) == file.lastModified()
    }

    private long loadInceptionTimestamp() {
        Long.parseLong(loadProperties(fileAccessPropertiesFile).getProperty(INCEPTION_TIMESTAMP_KEY))
    }

    private void writeInceptionTimestamp(long millis) {
        fileAccessPropertiesFile.text = "${INCEPTION_TIMESTAMP_KEY} = $millis"
    }

    private TestFile getFileAccessPropertiesFile() {
        cachesDir.file(CACHE_KEY, FILE_ACCESS_PROPERTIES_FILE_NAME)
    }
}
