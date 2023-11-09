/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.cache.LockOptions
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.filelock.LockOptionsBuilder
import org.gradle.cache.internal.locklistener.NoOpFileLockContentionHandler
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.progress.NoOpProgressLoggerFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.cache.FileLockManager.LockMode.Exclusive
import static org.gradle.cache.FileLockManager.LockMode.Shared

class DefaultCacheFactoryTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    final Action<?> opened = Mock()
    final Action<?> closed = Mock()
    final ProcessMetaDataProvider metaDataProvider = Mock()
    def progressLoggerFactory = new NoOpProgressLoggerFactory()
    private final DefaultCacheFactory factory = new DefaultCacheFactory(new DefaultFileLockManager(metaDataProvider, new NoOpFileLockContentionHandler()), Mock(ExecutorFactory), progressLoggerFactory) {
        @Override
        void onOpen(Object cache) {
            opened.execute(cache)
        }

        @Override
        void onClose(Object cache) {
            closed.execute(cache)
        }
    }

    def setup() {
        _ * metaDataProvider.processIdentifier >> '123'
        _ * metaDataProvider.processDisplayName >> 'process'
    }

    void "creates directory backed cache instance"() {
        when:
        def cache = factory.open(tmpDir.testDirectory, "<display>", [prop: 'value'], new LockOptionsBuilder(Shared), null, null)

        then:
        cache.reference.cache instanceof DefaultPersistentDirectoryCache
        cache.baseDir == tmpDir.testDirectory
        cache.toString().startsWith "<display>"

        cleanup:
        factory.close()
    }

    void "reuses directory backed cache instances"() {
        when:
        def ref1 = factory.open(tmpDir.testDirectory, null, [prop: 'value'], new LockOptionsBuilder(Exclusive), null, null)
        def ref2 = factory.open(tmpDir.testDirectory, null, [prop: 'value'], new LockOptionsBuilder(Exclusive), null, null)

        then:
        ref1.reference.cache.is(ref2.reference.cache)

        and:
        1 * opened.execute(_)
        0 * opened._

        cleanup:
        factory.close()
    }

    void "closes cache instance when factory is closed"() {
        def implementation

        when:
        factory.open(tmpDir.testDirectory, null, [prop: 'value'], new LockOptionsBuilder(Exclusive), null, null)

        then:
        1 * opened.execute(_) >> { DefaultPersistentDirectoryStore s -> implementation = s }
        0 * opened._

        when:
        factory.close()

        then:
        1 * closed.execute(implementation)
        0 * _
    }

    void "closes cache instance when reference is closed"() {
        def implementation

        when:
        def cache1 = factory.open(tmpDir.testDirectory, null, [prop: 'value'], new LockOptionsBuilder(Exclusive), null, null)
        def cache2 = factory.open(tmpDir.testDirectory, null, [prop: 'value'], new LockOptionsBuilder(Exclusive), null, null)

        then:
        1 * opened.execute(_) >> { DefaultPersistentDirectoryStore s -> implementation = s }
        0 * opened._

        when:
        cache1.close()

        then:
        0 * _

        when:
        cache2.close()

        then:
        1 * closed.execute(implementation)
        0 * _
    }

    void "can close cache multiple times"() {
        def implementation

        when:
        def cache = factory.open(tmpDir.testDirectory, null, [prop: 'value'], new LockOptionsBuilder(Exclusive), null, null)

        then:
        1 * opened.execute(_) >> { DefaultPersistentDirectoryStore s -> implementation = s }
        0 * opened._

        when:
        cache.close()
        cache.close()

        then:
        1 * closed.execute(implementation)
        0 * _
    }

    void "can close factory after closing cache"() {
        def implementation

        when:
        def cache = factory.open(tmpDir.testDirectory, null, [prop: 'value'], new LockOptionsBuilder(Exclusive), null, null)

        then:
        1 * opened.execute(_) >> { DefaultPersistentDirectoryStore s -> implementation = s }
        0 * opened._

        when:
        cache.close()
        factory.close()

        then:
        1 * closed.execute(implementation)
        0 * _
    }

    void "fails when directory cache is already open with different properties"() {
        given:
        factory.open(tmpDir.testDirectory, null, [prop: 'value'], new LockOptionsBuilder(Exclusive), null, null)

        when:
        factory.open(tmpDir.testDirectory, null, [prop: 'other'], new LockOptionsBuilder(Exclusive), null, null)

        then:
        IllegalStateException e = thrown()
        e.message == "Cache '${tmpDir.testDirectory}' is already open with different properties."

        cleanup:
        factory.close()
    }

    void "fails when directory cache when cache is already open with different lock mode"() {
        given:
        factory.open(tmpDir.testDirectory, null, [prop: 'value'], new LockOptionsBuilder(Shared), null, null)

        when:
        factory.open(tmpDir.testDirectory, null, [prop: 'other'], new LockOptionsBuilder(Exclusive), null, null)

        then:
        IllegalStateException e = thrown()
        e.message == "Cache '${tmpDir.testDirectory}' is already open with different lock options."

        cleanup:
        factory.close()
    }

    void "fails when directory cache when cache is already open with different lock target"() {
        given:
        factory.open(tmpDir.testDirectory, null, [prop: 'value'], new LockOptionsBuilder(Shared, false, null, LockOptions.LockTarget.CachePropertiesFile), null, null)

        when:
        factory.open(tmpDir.testDirectory, null, [prop: 'other'], new LockOptionsBuilder(Shared), null, null)

        then:
        IllegalStateException e = thrown()
        e.message == "Cache '${tmpDir.testDirectory}' is already open with different lock target."

        cleanup:
        factory.close()
    }

    void "can visit all caches created by factory"() {
        def visited = [] as Set

        when:
        factory.open(tmpDir.testDirectory.file('foo'), "foo", [prop: 'value'], new LockOptionsBuilder(Shared), null, null)
        factory.open(tmpDir.testDirectory.file('bar'), "bar", [prop: 'value'], new LockOptionsBuilder(Shared), null, null)
        factory.open(tmpDir.testDirectory.file('baz'), "baz", [prop: 'value'], new LockOptionsBuilder(Shared), null, null)

        and:
        factory.visitCaches(new CacheVisitor() {
            @Override
            void visit(PersistentCache cache) {
                visited << cache.displayName.split(' ')[0]
            }
        })

        then:
        visited.containsAll(['foo', 'bar', 'baz'])

        cleanup:
        factory.close()
    }

    void "does not visit caches that have been closed"() {
        def visited = [] as Set

        when:
        factory.open(tmpDir.testDirectory.file('foo'), "foo", [prop: 'value'], new LockOptionsBuilder(Shared), null, null)
        def bar = factory.open(tmpDir.testDirectory.file('bar'), "bar", [prop: 'value'], new LockOptionsBuilder(Shared), null, null)
        factory.open(tmpDir.testDirectory.file('baz'), "baz", [prop: 'value'], new LockOptionsBuilder(Shared), null, null)

        and:
        bar.close()

        and:
        factory.visitCaches(new CacheVisitor() {
            @Override
            void visit(PersistentCache cache) {
                visited << cache.displayName.split(' ')[0]
            }
        })

        then:
        visited.containsAll(['foo', 'baz'])
        ! visited.contains('bar')

        cleanup:
        factory.close()
    }
}
