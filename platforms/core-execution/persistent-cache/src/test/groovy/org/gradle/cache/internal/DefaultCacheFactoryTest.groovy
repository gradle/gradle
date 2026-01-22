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

import org.gradle.cache.CleanableStore
import org.gradle.cache.HasCleanupAction
import org.gradle.cache.internal.locklistener.NoOpFileLockContentionHandler
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.util.function.Consumer

import static org.gradle.cache.FileLockManager.LockMode.Exclusive
import static org.gradle.cache.FileLockManager.LockMode.Shared
import static org.gradle.cache.internal.filelock.DefaultLockOptions.mode

class DefaultCacheFactoryTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    final Consumer<?> opened = Mock()
    final Consumer<?> closed = Mock()
    final ProcessMetaDataProvider metaDataProvider = Mock()
    final File cacheDir = tmpDir.testDirectory.createDir("cache")

    private final DefaultCacheFactory factory = new DefaultCacheFactory(new DefaultFileLockManager(metaDataProvider, new NoOpFileLockContentionHandler()), Mock(ExecutorFactory)) {
        @Override
        void onOpen(Object cache) {
            opened.accept(cache)
        }

        @Override
        void onClose(Object cache) {
            closed.accept(cache)
        }
    }

    def setup() {
        _ * metaDataProvider.processIdentifier >> '123'
        _ * metaDataProvider.processDisplayName >> 'process'
    }

    void "creates directory backed #kind cache instance"() {
        when:
        def cache = open(kind)

        then:
        expectedImpl.isAssignableFrom(cache.reference.cache.class)
        cache.baseDir == cacheDir
        cache.toString().startsWith(expectedDisplayName)

        cleanup:
        factory.close()

        where:
        kind     | expectedImpl                      | expectedDisplayName
        'coarse' | DefaultPersistentDirectoryCache   | "<coarse>"
        'fine'   | DefaultFineGrainedPersistentCache | "<fine>"
    }

    void "reuses directory backed #kind cache instances"() {
        when:
        def ref1 = open(kind)
        def ref2 = open(kind)

        then:
        ref1.reference.cache.is(ref2.reference.cache)

        and:
        1 * opened.accept(_) >> { Closeable s -> assert expectedOpenedClass.isAssignableFrom(s.getClass()) }
        0 * opened._

        cleanup:
        factory.close()

        where:
        kind     | expectedOpenedClass
        'coarse' | DefaultPersistentDirectoryStore
        'fine'   | DefaultFineGrainedPersistentCache
    }

    void "closes #kind cache instance when factory is closed"() {
        def implementation

        when:
        open(kind)

        then:
        1 * opened.accept(_) >> { Closeable s -> implementation = s }
        0 * opened._

        when:
        factory.close()

        then:
        1 * closed.accept(implementation)
        0 * _

        where:
        kind << ['coarse', 'fine']
    }

    void "closes #kind cache instance when reference is closed"() {
        def implementation

        when:
        def cache1 = open(kind)
        def cache2 = open(kind)

        then:
        1 * opened.accept(_) >> { Closeable s -> implementation = s }
        0 * opened._

        when:
        cache1.close()

        then:
        0 * _

        when:
        cache2.close()

        then:
        1 * closed.accept(implementation)
        0 * _

        where:
        kind << ['coarse', 'fine']
    }

    void "can close #kind cache multiple times"() {
        def implementation

        when:
        def cache = open(kind)

        then:
        1 * opened.accept(_) >> { Closeable s -> implementation = s }
        0 * opened._

        when:
        cache.close()
        cache.close()

        then:
        1 * closed.accept(implementation)
        0 * _

        where:
        kind << ['coarse', 'fine']
    }

    void "can close factory after closing #kind cache"() {
        def implementation

        when:
        def cache = open(kind)

        then:
        1 * opened.accept(_) >> { Closeable s -> implementation = s }
        0 * opened._

        when:
        cache.close()
        factory.close()

        then:
        1 * closed.accept(implementation)
        0 * _

        where:
        kind << ['coarse', 'fine']
    }

    void "fails when directory cache is already open with different properties"() {
        given:
        factory.open(tmpDir.testDirectory, null, [prop: 'value'], mode(Exclusive), null, null)

        when:
        factory.open(tmpDir.testDirectory, null, [prop: 'other'], mode(Exclusive), null, null)

        then:
        IllegalStateException e = thrown()
        e.message == "Cache '${tmpDir.testDirectory}' is already open with different properties."

        cleanup:
        factory.close()
    }

    void "fails when directory cache when cache is already open with different lock mode"() {
        given:
        factory.open(tmpDir.testDirectory, null, [prop: 'value'], mode(Shared), null, null)

        when:
        factory.open(tmpDir.testDirectory, null, [prop: 'other'], mode(Exclusive), null, null)

        then:
        IllegalStateException e = thrown()
        e.message == "Cache '${tmpDir.testDirectory}' is already open with different lock options."

        cleanup:
        factory.close()
    }

    void "fails when directory cache is already open with different cache type"() {
        when:
        factory.open(tmpDir.testDirectory.file("foo"), "foo", [prop: 'value'], mode(Exclusive), null, null)
        factory.openFineGrained(tmpDir.file("foo"), "foo", {  })

        then:
        IllegalStateException e = thrown()
        e.message == "Cache '${tmpDir.testDirectory.file("foo")}' is already open as 'org.gradle.cache.internal.DefaultPersistentDirectoryCache' that is not a subtype of expected 'org.gradle.cache.FineGrainedPersistentCache'."

        when:
        factory.openFineGrained(tmpDir.file("bar"), null, {  })
        factory.open(tmpDir.testDirectory.file("bar"), null, [prop: 'value'], mode(Exclusive), null, null)


        then:
        e = thrown()
        e.message == "Cache '${tmpDir.testDirectory.file("bar")}' is already open as 'org.gradle.cache.internal.DefaultFineGrainedPersistentCache' that is not a subtype of expected 'org.gradle.cache.PersistentCache'."

        cleanup:
        factory.close()
    }

    void "can visit all caches created by factory"() {
        def visited = [] as Set

        when:
        factory.open(tmpDir.testDirectory.file('foo'), "foo", [prop: 'value'], mode(Shared), null, null)
        factory.open(tmpDir.testDirectory.file('bar'), "bar", [prop: 'value'], mode(Shared), null, null)
        factory.openFineGrained(tmpDir.testDirectory.file('baz'), "baz", {})
        factory.openFineGrained(tmpDir.testDirectory.file('qux'), "qux", {})

        and:
        factory.visitCaches(new CacheVisitor() {
            @Override
            <T extends CleanableStore & HasCleanupAction> void visit(T cache) {
                visited << cache.displayName.split(' ')[0]
            }
        })

        then:
        visited.containsAll(['foo', 'bar', 'baz', 'qux'])

        cleanup:
        factory.close()
    }

    void "does not visit caches that have been closed"() {
        def visited = [] as Set

        when:
        factory.open(tmpDir.testDirectory.file('foo'), "foo", [prop: 'value'], mode(Shared), null, null)
        def bar = factory.open(tmpDir.testDirectory.file('bar'), "bar", [prop: 'value'], mode(Shared), null, null)
        factory.openFineGrained(tmpDir.testDirectory.file('baz'), "baz", {  })
        def qux = factory.openFineGrained(tmpDir.testDirectory.file('qux'), "qux", {  })

        and:
        bar.close()
        qux.close()

        and:
        factory.visitCaches(new CacheVisitor() {
            @Override
            <T extends CleanableStore & HasCleanupAction> void visit(T cache) {
                visited << cache.displayName.split(' ')[0]
            }
        })

        then:
        visited.containsAll(['foo', 'baz'])
        !visited.contains('bar')
        !visited.contains('qux')

        cleanup:
        factory.close()
    }

    private def open(String kind) {
        switch (kind) {
            case 'coarse':
                return factory.open(cacheDir, '<coarse>', [prop: 'value'], mode(Exclusive), null, null)
            case 'fine':
                return factory.openFineGrained(cacheDir, '<fine>', { })
            default:
                throw new IllegalArgumentException("Unknown kind: $kind")
        }
    }
}
