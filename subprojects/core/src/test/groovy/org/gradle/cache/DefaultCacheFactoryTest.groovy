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
package org.gradle.cache

import org.gradle.CacheUsage
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification
import org.gradle.cache.btree.BTreePersistentIndexedCache

class DefaultCacheFactoryTest extends Specification {
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder()
    private final DefaultCacheFactory factoryFactory = new DefaultCacheFactory()

    public void "creates directory backed cache instance"() {
        when:
        def factory = factoryFactory.create()
        def cache = factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])

        then:
        cache instanceof DefaultPersistentDirectoryCache
        cache.baseDir == tmpDir.dir
    }

    public void "creates indexed cache instance"() {
        when:
        def factory = factoryFactory.create()
        def cache = factory.openIndexedCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], new DefaultSerializer())

        then:
        cache instanceof BTreePersistentIndexedCache
    }

    public void "creates state cache instance"() {
        when:
        def factory = factoryFactory.create()
        def cache = factory.openStateCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], new DefaultSerializer())

        then:
        cache instanceof SimpleStateCache
    }

    public void "reuses directory backed cache instances"() {
        when:
        def factory = factoryFactory.create()
        def ref1 = factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])
        def ref2 = factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])

        then:
        ref1.is(ref2)
    }

    public void "reuses directory backed cache instances across multiple sessions"() {
        when:
        def factory1 = factoryFactory.create()
        def factory2 = factoryFactory.create()
        def ref1 = factory1.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])
        def ref2 = factory2.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])

        then:
        ref1.is(ref2)
    }

    public void "reuses indexed cache instances"() {
        when:
        def factory = factoryFactory.create()
        def ref1 = factory.openIndexedCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], null)
        def ref2 = factory.openIndexedCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], null)

        then:
        ref1.is(ref2)
    }

    public void "reuses indexed cache instances across multiple sessions"() {
        when:
        def factory1 = factoryFactory.create()
        def factory2 = factoryFactory.create()
        def ref1 = factory1.openIndexedCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], null)
        def ref2 = factory2.openIndexedCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], null)

        then:
        ref1.is(ref2)
    }

    public void "reuses state cache instances"() {
        when:
        def factory = factoryFactory.create()
        def ref1 = factory.openStateCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], null)
        def ref2 = factory.openStateCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], null)

        then:
        ref1.is(ref2)
    }

    public void "reuses state cache instances across multiple sessions"() {
        when:
        def factory1 = factoryFactory.create()
        def factory2 = factoryFactory.create()
        def ref1 = factory1.openStateCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], null)
        def ref2 = factory2.openStateCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], null)

        then:
        ref1.is(ref2)
    }

    public void "releases directory cache instance when last reference released"() {
        given:
        def factory1 = factoryFactory.create()
        def factory2 = factoryFactory.create()
        factory1.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])
        def oldCache = factory2.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])
        factory1.close()
        factory2.close()

        when:
        def factory = factoryFactory.create()
        def cache = factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])

        then:
        !cache.is(oldCache)
    }

    public void "releases index cache instance when last reference released"() {
        given:
        def factory1 = factoryFactory.create()
        def factory2 = factoryFactory.create()
        factory1.openIndexedCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], null)
        def oldCache = factory2.openIndexedCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], null)
        factory1.close()
        factory2.close()

        when:
        def factory = factoryFactory.create()
        def cache = factory.openIndexedCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], null)

        then:
        !cache.is(oldCache)
    }

    public void "releases state cache instance when last reference released"() {
        given:
        def factory1 = factoryFactory.create()
        def factory2 = factoryFactory.create()
        factory1.openStateCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], null)
        def oldCache = factory2.openStateCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], null)
        factory1.close()
        factory2.close()

        when:
        def factory = factoryFactory.create()
        def cache = factory.openStateCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], null)

        then:
        !cache.is(oldCache)
    }

    public void "can open and release cache as directory and indexed and state cache"() {
        given:
        def factory1 = factoryFactory.create()
        def factory2 = factoryFactory.create()
        def oldCache = factory1.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])
        factory2.openIndexedCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], null)
        factory2.openStateCache(tmpDir.dir, CacheUsage.ON, [prop: 'value'], null)
        factory2.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])
        factory1.close()
        factory2.close()

        when:
        def factory = factoryFactory.create()
        def cache = factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])

        then:
        !oldCache.is(cache)
    }

    public void "fails when directory cache is already open with different properties"() {
        given:
        def factory = factoryFactory.create()
        factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])

        when:
        factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'other'])

        then:
        IllegalStateException e = thrown()
        e.message == "Cache '${tmpDir.dir}' is already open with different state."
    }

    public void "fails when directory cache is already open with different properties in different session"() {
        given:
        def factory1 = factoryFactory.create()
        factory1.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])

        when:
        def factory2 = factoryFactory.create()
        factory2.open(tmpDir.dir, CacheUsage.ON, [prop: 'other'])

        then:
        IllegalStateException e = thrown()
        e.message == "Cache '${tmpDir.dir}' is already open with different state."
    }

    public void "fails when directory cache is already open when rebuild is requested"() {
        given:
        def factory = factoryFactory.create()
        factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])

        when:
        factory.open(tmpDir.dir, CacheUsage.REBUILD, [prop: 'value'])

        then:
        IllegalStateException e = thrown()
        e.message == "Cannot rebuild cache '${tmpDir.dir}' as it is already open."
    }

    public void "fails when directory cache is already open in different session when rebuild is requested"() {
        given:
        def factory1 = factoryFactory.create()
        factory1.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])

        when:
        def factory2 = factoryFactory.create()
        factory2.open(tmpDir.dir, CacheUsage.REBUILD, [prop: 'value'])

        then:
        IllegalStateException e = thrown()
        e.message == "Cannot rebuild cache '${tmpDir.dir}' as it is already open."
    }

    public void "can open directory cache when rebuild is requested and cache was rebuilt in same session"() {
        given:
        def factory = factoryFactory.create()
        factory.open(tmpDir.dir, CacheUsage.REBUILD, [prop: 'value'])

        when:
        factory.open(tmpDir.dir, CacheUsage.REBUILD, [prop: 'value'])

        then:
        notThrown(RuntimeException)
    }

    public void "can open directory cache when rebuild is requested and has been closed"() {
        given:
        def factory1 = factoryFactory.create()
        factory1.open(tmpDir.dir, CacheUsage.REBUILD, [prop: 'value'])
        factory1.close()

        when:
        def factory2 = factoryFactory.create()
        factory2.open(tmpDir.dir, CacheUsage.REBUILD, [prop: 'value'])

        then:
        notThrown(RuntimeException)
    }
}



