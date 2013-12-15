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

import org.gradle.CacheUsage
import org.gradle.api.Action
import org.gradle.cache.CacheValidator
import org.gradle.cache.internal.locklistener.NoOpFileLockContentionHandler
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.cache.internal.FileLockManager.LockMode.*
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode

class DefaultCacheFactoryTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final Action<?> opened = Mock()
    final Action<?> closed = Mock()
    final ProcessMetaDataProvider metaDataProvider = Mock()
    private final DefaultCacheFactory factoryFactory = new DefaultCacheFactory(new DefaultFileLockManager(metaDataProvider, new NoOpFileLockContentionHandler())) {
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

    def cleanup() {
        factoryFactory.close()
    }

    public void "creates directory backed store instance"() {
        when:
        def factory = factoryFactory.create()
        def cache = factory.openStore(tmpDir.testDirectory, "<display>", mode(Shared), null)

        then:
        cache.reference.cache instanceof DefaultPersistentDirectoryStore
        cache.baseDir == tmpDir.testDirectory
        cache.toString().startsWith "<display>"
    }

    public void "creates directory backed cache instance"() {
        when:
        def factory = factoryFactory.create()
        def cache = factory.open(tmpDir.testDirectory, "<display>", CacheUsage.ON, null, [prop: 'value'], mode(Shared), null)

        then:
        cache.reference.cache instanceof DefaultPersistentDirectoryCache
        cache.baseDir == tmpDir.testDirectory
        cache.toString().startsWith "<display>"
    }

    public void "creates DelegateOnDemandPersistentDirectoryCache cache instance for LockMode.NONE"() {
        when:
        def factory = factoryFactory.create()
        def cache = factory.open(tmpDir.testDirectory, "<display>", CacheUsage.ON, null, [prop: 'value'], mode(None), null)

        then:
        cache.reference.cache instanceof DelegateOnDemandPersistentDirectoryCache
        cache.baseDir == tmpDir.testDirectory
        cache.toString().startsWith "On Demand Cache for <display>"
    }

    public void "reuses directory backed cache instances"() {
        when:
        def factory = factoryFactory.create()
        def ref1 = factory.open(tmpDir.testDirectory, null, CacheUsage.ON, null, [prop: 'value'], mode(Exclusive), null)
        def ref2 = factory.open(tmpDir.testDirectory, null, CacheUsage.ON, null, [prop: 'value'], mode(Exclusive), null)

        then:
        ref1.reference.cache.is(ref2.reference.cache)

        and:
        1 * opened.execute(_)
        0 * opened._
    }

    public void "reuses directory backed cache instances across multiple sessions"() {
        when:
        def factory1 = factoryFactory.create()
        def factory2 = factoryFactory.create()
        def ref1 = factory1.open(tmpDir.testDirectory, null, CacheUsage.ON, null, [prop: 'value'], mode(Exclusive), null)
        def ref2 = factory2.open(tmpDir.testDirectory, null, CacheUsage.ON, null, [prop: 'value'], mode(Exclusive), null)

        then:
        ref1.reference.cache.is(ref2.reference.cache)

        and:
        1 * opened.execute(_)
        0 * opened._
    }

    public void "reuses directory backed store instances"() {
        when:
        def factory = factoryFactory.create()
        def ref1 = factory.openStore(tmpDir.testDirectory, null, mode(Exclusive), null)
        def ref2 = factory.openStore(tmpDir.testDirectory, null, mode(Exclusive), null)

        then:
        ref1.reference.cache.is(ref2.reference.cache)

        and:
        1 * opened.execute(_)
        0 * opened._
    }

    public void "reuses directory backed store instances across multiple sessions"() {
        when:
        def factory1 = factoryFactory.create()
        def factory2 = factoryFactory.create()
        def ref1 = factory1.openStore(tmpDir.testDirectory, null, mode(Exclusive), null)
        def ref2 = factory2.openStore(tmpDir.testDirectory, null, mode(Exclusive), null)

        then:
        ref1.reference.cache.is(ref2.reference.cache)

        and:
        1 * opened.execute(_)
        0 * opened._
    }

    public void "closes cache instance when factory is closed"() {
        def implementation

        when:
        def factory1 = factoryFactory.create()
        def factory2 = factoryFactory.create()
        factory1.open(tmpDir.testDirectory, null, CacheUsage.ON, null, [prop: 'value'], mode(Exclusive), null)
        factory2.open(tmpDir.testDirectory, null, CacheUsage.ON, null, [prop: 'value'], mode(Exclusive), null)

        then:
        1 * opened.execute(_) >> { DefaultPersistentDirectoryStore s -> implementation = s }
        0 * opened._

        when:
        factoryFactory.close()

        then:
        1 * closed.execute(implementation)
        0 * _
    }

    public void "closes cache instance when last session is closed"() {
        def implementation

        when:
        def factory1 = factoryFactory.create()
        def factory2 = factoryFactory.create()
        factory1.open(tmpDir.testDirectory, null, CacheUsage.ON, null, [prop: 'value'], mode(Exclusive), null)
        factory2.open(tmpDir.testDirectory, null, CacheUsage.ON, null, [prop: 'value'], mode(Exclusive), null)

        then:
        1 * opened.execute(_) >> { DefaultPersistentDirectoryStore s -> implementation = s }
        0 * opened._

        when:
        factory1.close()

        then:
        0 * _

        when:
        factory2.close()

        then:
        1 * closed.execute(implementation)
        0 * _

        when:
        def factory = factoryFactory.create()
        factory.open(tmpDir.testDirectory, null, CacheUsage.ON, null, [prop: 'value'], mode(Exclusive), null)

        then:
        1 * opened.execute({it != implementation})
        0 * closed._
    }

    public void "releases cache instance when reference is closed"() {
        def implementation

        when:
        def factory = factoryFactory.create()
        def cache1 = factory.open(tmpDir.testDirectory, null, CacheUsage.ON, null, [prop: 'value'], mode(Exclusive), null)
        def cache2 = factory.open(tmpDir.testDirectory, null, CacheUsage.ON, null, [prop: 'value'], mode(Exclusive), null)

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

    public void "can close cache multiple times"() {
        def implementation

        when:
        def factory = factoryFactory.create()
        def cache = factory.open(tmpDir.testDirectory, null, CacheUsage.ON, null, [prop: 'value'], mode(Exclusive), null)

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

    public void "can close session after closing cache"() {
        def implementation

        when:
        def factory = factoryFactory.create()
        def cache = factory.open(tmpDir.testDirectory, null, CacheUsage.ON, null, [prop: 'value'], mode(Exclusive), null)

        then:
        1 * opened.execute(_) >> { DefaultPersistentDirectoryStore s -> implementation = s }
        0 * opened._

        when:
        cache.close()
        factory.close()
        factoryFactory.close()

        then:
        1 * closed.execute(implementation)
        0 * _
    }

    public void "fails when directory cache is already open with different properties"() {
        given:
        def factory = factoryFactory.create()
        factory.open(tmpDir.testDirectory, null, CacheUsage.ON, null, [prop: 'value'], mode(Exclusive), null)

        when:
        factory.open(tmpDir.testDirectory, null, CacheUsage.ON, null, [prop: 'other'], mode(Exclusive), null)

        then:
        IllegalStateException e = thrown()
        e.message == "Cache '${tmpDir.testDirectory}' is already open with different state."
    }

    public void "fails when directory cache is already open with different properties in different session"() {
        given:
        def factory1 = factoryFactory.create()
        factory1.open(tmpDir.testDirectory, null, CacheUsage.ON, null, [prop: 'value'], mode(Exclusive), null)

        when:
        def factory2 = factoryFactory.create()
        factory2.open(tmpDir.testDirectory, null, CacheUsage.ON, null, [prop: 'other'], mode(Exclusive), null)

        then:
        IllegalStateException e = thrown()
        e.message == "Cache '${tmpDir.testDirectory}' is already open with different state."
    }

    public void "fails when directory cache is already open when rebuild is requested"() {
        given:
        def factory = factoryFactory.create()
        factory.open(tmpDir.testDirectory, null, CacheUsage.ON, null, [prop: 'value'], mode(Exclusive), null)

        when:
        factory.open(tmpDir.testDirectory, null, CacheUsage.REBUILD, null, [prop: 'value'], mode(Exclusive), null)

        then:
        IllegalStateException e = thrown()
        e.message == "Cannot rebuild cache '${tmpDir.testDirectory}' as it is already open."
    }

    public void "fails when directory cache is already open in different session when rebuild is requested"() {
        given:
        def factory1 = factoryFactory.create()
        factory1.open(tmpDir.testDirectory, null, CacheUsage.ON, null, [prop: 'value'], mode(Exclusive), null)

        when:
        def factory2 = factoryFactory.create()
        factory2.open(tmpDir.testDirectory, null, CacheUsage.REBUILD, null, [prop: 'value'], mode(Exclusive), null)

        then:
        IllegalStateException e = thrown()
        e.message == "Cannot rebuild cache '${tmpDir.testDirectory}' as it is already open."
    }

    public void "can open directory cache when rebuild is requested and cache was rebuilt in same session"() {
        given:
        def factory = factoryFactory.create()
        factory.open(tmpDir.testDirectory, null, CacheUsage.REBUILD, null, [prop: 'value'], mode(Exclusive), null)

        when:
        factory.open(tmpDir.testDirectory, null, CacheUsage.REBUILD, null, [prop: 'value'], mode(Exclusive), null)

        then:
        notThrown(RuntimeException)
    }

    public void "can open directory cache when rebuild is requested and has been closed"() {
        given:
        def factory1 = factoryFactory.create()
        factory1.open(tmpDir.testDirectory, null, CacheUsage.REBUILD, null, [prop: 'value'], mode(Exclusive), null)
        factory1.close()

        when:
        def factory2 = factoryFactory.create()
        factory2.open(tmpDir.testDirectory, null, CacheUsage.REBUILD, null, [prop: 'value'], mode(Exclusive), null)

        then:
        notThrown(RuntimeException)
    }

    public void "fails when directory cache when cache is already open with different lock mode"() {
        given:
        def factory = factoryFactory.create()
        factory.open(tmpDir.testDirectory, null, CacheUsage.ON, null, [prop: 'value'], mode(Shared), null)

        when:
        factory.open(tmpDir.testDirectory, null, CacheUsage.ON, null, [prop: 'other'], mode(Exclusive), null)

        then:
        IllegalStateException e = thrown()
        e.message == "Cache '${tmpDir.testDirectory}' is already open with different options."
    }

    public void "can pass CacheValidator to Cache"() {
        given:
        def factory1 = factoryFactory.create()
        CacheValidator validator = Mock()

        when:
        def cache = factory1.open(tmpDir.testDirectory, null, CacheUsage.ON, validator, [prop: 'value'], mode(Shared), null)

        then:
        validator.isValid() >>> [false, true]
        cache != null
    }
}
