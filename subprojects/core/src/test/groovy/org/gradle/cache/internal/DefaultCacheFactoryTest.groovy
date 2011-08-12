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
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache
import org.gradle.api.Action

import org.gradle.cache.DefaultSerializer
import org.gradle.cache.internal.CacheFactory.CrossVersionMode

class DefaultCacheFactoryTest extends Specification {
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder()
    final Action<?> opened = Mock()
    final Action<?> closed = Mock()
    private final DefaultCacheFactory factoryFactory = new DefaultCacheFactory() {
        @Override
        void onOpen(Object cache) {
            opened.execute(cache)
        }

        @Override
        void onClose(Object cache) {
            closed.execute(cache)
        }
    }

    def cleanup() {
        factoryFactory.close()
    }

    public void "creates directory backed store instance"() {
        when:
        def factory = factoryFactory.create()
        def cache = factory.openStore(tmpDir.dir, FileLockManager.LockMode.Shared, CrossVersionMode.VersionSpecific, null)

        then:
        cache instanceof DefaultPersistentDirectoryStore
        cache.baseDir == tmpDir.dir
    }

    public void "creates directory backed cache instance"() {
        when:
        def factory = factoryFactory.create()
        def cache = factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Shared, CrossVersionMode.VersionSpecific, null)

        then:
        cache instanceof DefaultPersistentDirectoryCache
        cache.baseDir == tmpDir.dir
    }

    public void "creates indexed cache instance"() {
        when:
        def factory = factoryFactory.create()
        def cache = factory.openIndexedCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, new DefaultSerializer())

        then:
        cache instanceof BTreePersistentIndexedCache
    }

    public void "creates state cache instance"() {
        when:
        def factory = factoryFactory.create()
        def cache = factory.openStateCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, new DefaultSerializer())

        then:
        cache instanceof SimpleStateCache
    }

    public void "reuses directory backed cache instances"() {
        when:
        def factory = factoryFactory.create()
        def ref1 = factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)
        def ref2 = factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        then:
        ref1.is(ref2)
        0 * closed._
    }

    public void "reuses directory backed cache instances across multiple sessions"() {
        when:
        def factory1 = factoryFactory.create()
        def factory2 = factoryFactory.create()
        def ref1 = factory1.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)
        def ref2 = factory2.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        then:
        ref1.is(ref2)
        0 * closed._
    }

    public void "reuses indexed cache instances"() {
        when:
        def factory = factoryFactory.create()
        def ref1 = factory.openIndexedCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)
        def ref2 = factory.openIndexedCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        then:
        ref1.is(ref2)
        0 * closed._
    }

    public void "reuses indexed cache instances across multiple sessions"() {
        when:
        def factory1 = factoryFactory.create()
        def factory2 = factoryFactory.create()
        def ref1 = factory1.openIndexedCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)
        def ref2 = factory2.openIndexedCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        then:
        ref1.is(ref2)
        0 * closed._
    }

    public void "reuses state cache instances"() {
        when:
        def factory = factoryFactory.create()
        def ref1 = factory.openStateCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)
        def ref2 = factory.openStateCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        then:
        ref1.is(ref2)
        0 * closed._
    }

    public void "reuses state cache instances across multiple sessions"() {
        when:
        def factory1 = factoryFactory.create()
        def factory2 = factoryFactory.create()
        def ref1 = factory1.openStateCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)
        def ref2 = factory2.openStateCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        then:
        ref1.is(ref2)
        0 * closed._
    }

    public void "releases directory cache instance when last reference released"() {
        given:
        def factory1 = factoryFactory.create()
        def factory2 = factoryFactory.create()
        factory1.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)
        def oldCache = factory2.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        when:
        factory1.close()
        factory2.close()

        then:
        1 * closed.execute(!null)

        when:
        def factory = factoryFactory.create()
        def cache = factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        then:
        !cache.is(oldCache)
        1 * opened.execute(!null)
        0 * closed._
    }

    public void "releases index cache instance and backing directory instance when last reference released"() {
        given:
        def factory1 = factoryFactory.create()
        def factory2 = factoryFactory.create()
        factory1.openIndexedCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)
        def oldCache = factory2.openIndexedCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        when:
        factory1.close()
        factory2.close()

        then:
        2 * closed.execute(!null)

        when:
        def factory = factoryFactory.create()
        def cache = factory.openIndexedCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        then:
        !cache.is(oldCache)
        2 * opened.execute(!null)
        0 * closed._
    }

    public void "releases state cache instance and backing directory instance when last reference released"() {
        given:
        def factory1 = factoryFactory.create()
        def factory2 = factoryFactory.create()
        factory1.openStateCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)
        def oldCache = factory2.openStateCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        when:
        factory1.close()
        factory2.close()

        then:
        2 * closed.execute(!null)

        when:
        def factory = factoryFactory.create()
        def cache = factory.openStateCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        then:
        !cache.is(oldCache)
        2 * opened.execute(!null)
        0 * closed._
    }

    public void "can open and release cache as directory and indexed and state cache"() {
        given:
        def factory1 = factoryFactory.create()
        def factory2 = factoryFactory.create()
        def oldCache = factory1.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)
        factory2.openIndexedCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)
        factory2.openStateCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)
        factory2.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        when:
        factory1.close()
        factory2.close()

        then:
        3 * closed.execute(!null)

        when:
        def factory = factoryFactory.create()
        def cache = factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        then:
        !oldCache.is(cache)
        1 * opened.execute(!null)
        0 * closed._
    }

    public void "fails when directory cache is already open with different properties"() {
        given:
        def factory = factoryFactory.create()
        factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        when:
        factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'other'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        then:
        IllegalStateException e = thrown()
        e.message == "Cache '${tmpDir.dir}' is already open with different state."
    }

    public void "fails when directory cache is already open with different properties in different session"() {
        given:
        def factory1 = factoryFactory.create()
        factory1.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        when:
        def factory2 = factoryFactory.create()
        factory2.open(tmpDir.dir, CacheUsage.ON, [prop: 'other'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        then:
        IllegalStateException e = thrown()
        e.message == "Cache '${tmpDir.dir}' is already open with different state."
    }

    public void "fails when directory cache is already open when rebuild is requested"() {
        given:
        def factory = factoryFactory.create()
        factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        when:
        factory.open(tmpDir.dir, CacheUsage.REBUILD, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        then:
        IllegalStateException e = thrown()
        e.message == "Cannot rebuild cache '${tmpDir.dir}' as it is already open."
    }

    public void "fails when directory cache is already open in different session when rebuild is requested"() {
        given:
        def factory1 = factoryFactory.create()
        factory1.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        when:
        def factory2 = factoryFactory.create()
        factory2.open(tmpDir.dir, CacheUsage.REBUILD, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        then:
        IllegalStateException e = thrown()
        e.message == "Cannot rebuild cache '${tmpDir.dir}' as it is already open."
    }

    public void "can open directory cache when rebuild is requested and cache was rebuilt in same session"() {
        given:
        def factory = factoryFactory.create()
        factory.open(tmpDir.dir, CacheUsage.REBUILD, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        when:
        factory.open(tmpDir.dir, CacheUsage.REBUILD, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        then:
        notThrown(RuntimeException)
    }

    public void "can open directory cache when rebuild is requested and has been closed"() {
        given:
        def factory1 = factoryFactory.create()
        factory1.open(tmpDir.dir, CacheUsage.REBUILD, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)
        factory1.close()

        when:
        def factory2 = factoryFactory.create()
        factory2.open(tmpDir.dir, CacheUsage.REBUILD, [prop: 'value'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        then:
        notThrown(RuntimeException)
    }

    public void "fails when directory cache when cache is already open with different lock mode"() {
        given:
        def factory = factoryFactory.create()
        factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'], FileLockManager.LockMode.Shared, CrossVersionMode.VersionSpecific, null)

        when:
        factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'other'], FileLockManager.LockMode.Exclusive, CrossVersionMode.VersionSpecific, null)

        then:
        IllegalStateException e = thrown()
        e.message == "Cannot open cache '${tmpDir.dir}' with exclusive lock mode as it is already open with shared lock mode."
    }
}



