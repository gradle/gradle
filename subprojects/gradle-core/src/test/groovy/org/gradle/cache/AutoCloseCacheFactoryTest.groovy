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
        PersistentCache cache = Mock()

        when:
        def retval = factory.open(new File('dir1'), CacheUsage.ON, [:])

        then:
        1 * backingFactory.open(new File('dir1'), CacheUsage.ON, [:]) >> cache
        retval == cache
    }

    public void cachesCacheInstanceForAGivenDirectory() {
        PersistentCache cache = Mock()

        when:
        def cache1 = factory.open(new File('dir1'), CacheUsage.ON, [:])
        def cache2 = factory.open(new File('dir1').canonicalFile, CacheUsage.ON, [:])

        then:
        1 * backingFactory.open(new File('dir1'), CacheUsage.ON, [:]) >> cache
        cache1 == cache2
    }

    public void closesCacheUsingBackingFactory() {
        PersistentCache cache = Mock()

        when:
        factory.open(new File('dir1'), CacheUsage.ON, [:])

        then:
        1 * backingFactory.open(new File('dir1'), CacheUsage.ON, [:]) >> cache

        when:
        factory.close(cache)

        then:
        1 * backingFactory.close(cache)
    }

    public void closesCacheWhenLastReferenceClosed() {
        PersistentCache cache = Mock()

        when:
        factory.open(new File('dir1'), CacheUsage.ON, [:])
        factory.open(new File('dir1'), CacheUsage.ON, [:])

        then:
        1 * backingFactory.open(new File('dir1'), CacheUsage.ON, [:]) >> cache

        when:
        factory.close(cache)
        factory.close(cache)

        then:
        1 * backingFactory.close(cache)
    }
    
    public void closesEachOpenCacheOnClose() {
        PersistentCache cache1 = Mock()
        PersistentCache cache2 = Mock()

        when:
        factory.open(new File('dir1'), CacheUsage.ON, [:])
        factory.open(new File('dir2'), CacheUsage.ON, [:])

        then:
        1 * backingFactory.open(new File('dir1'), CacheUsage.ON, [:]) >> cache1
        1 * backingFactory.open(new File('dir2'), CacheUsage.ON, [:]) >> cache2

        when:
        factory.close()

        then:
        1 * backingFactory.close(cache1)
        1 * backingFactory.close(cache2)
    }
}

