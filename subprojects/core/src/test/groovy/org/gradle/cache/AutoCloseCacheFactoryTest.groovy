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
import spock.lang.Specification

public class AutoCloseCacheFactoryTest extends Specification {
    private final CacheFactory backingFactory = Mock()
    private final AutoCloseCacheFactory factory = new AutoCloseCacheFactory(backingFactory)

    public void createsCachesUsingBackingFactory() {
        CacheFactory.CacheReference<PersistentCache> backingRef = Mock()
        PersistentCache cache = Mock()

        given:
        _ * backingRef.cache >> cache

        when:
        def retval = factory.open(new File('dir1'), CacheUsage.ON, [:])

        then:
        1 * backingFactory.open(new File('dir1'), CacheUsage.ON, [:]) >> backingRef
        retval.cache == cache
    }

    public void cachesCacheInstanceForAGivenDirectory() {
        CacheFactory.CacheReference<PersistentCache> backingRef = Mock()
        PersistentCache cache = Mock()

        given:
        _ * backingRef.cache >> cache

        when:
        def cache1 = factory.open(new File('dir1'), CacheUsage.ON, [:])
        def cache2 = factory.open(new File('dir1').canonicalFile, CacheUsage.ON, [:])

        then:
        1 * backingFactory.open(new File('dir1'), CacheUsage.ON, [:]) >> backingRef
        cache1 == cache2
    }

    public void closesCacheUsingBackingFactory() {
        CacheFactory.CacheReference<PersistentCache> backingRef = Mock()
        PersistentCache cache = Mock()

        given:
        _ * backingRef.cache >> cache

        when:
        def cacheRef = factory.open(new File('dir1'), CacheUsage.ON, [:])

        then:
        1 * backingFactory.open(new File('dir1'), CacheUsage.ON, [:]) >> backingRef

        when:
        cacheRef.release()

        then:
        1 * backingRef.release()
    }

    public void closesCacheWhenLastReferenceClosed() {
        CacheFactory.CacheReference<PersistentCache> backingRef = Mock()
        PersistentCache cache = Mock()

        given:
        _ * backingRef.cache >> cache

        when:
        def cacheRef = factory.open(new File('dir1'), CacheUsage.ON, [:])
        factory.open(new File('dir1'), CacheUsage.ON, [:])

        then:
        1 * backingFactory.open(new File('dir1'), CacheUsage.ON, [:]) >> backingRef

        when:
        cacheRef.release()

        then:
        0 * backingRef._

        when:
        cacheRef.release()

        then:
        1 * backingRef.release()
    }
    
    public void closesEachOpenCacheOnClose() {
        CacheFactory.CacheReference<PersistentCache> backingRef1 = Mock()
        CacheFactory.CacheReference<PersistentCache> backingRef2 = Mock()
        PersistentCache cache1 = Mock()
        PersistentCache cache2 = Mock()

        given:
        _ * backingRef1.cache >> cache1
        _ * backingRef2.cache >> cache2

        when:
        factory.open(new File('dir1'), CacheUsage.ON, [:])
        factory.open(new File('dir2'), CacheUsage.ON, [:])

        then:
        1 * backingFactory.open(new File('dir1'), CacheUsage.ON, [:]) >> backingRef1
        1 * backingFactory.open(new File('dir2'), CacheUsage.ON, [:]) >> backingRef2

        when:
        factory.close()

        then:
        1 * backingRef1.release()
        1 * backingRef2.release()
    }
}

