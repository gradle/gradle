/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.cache.FileAccess
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache
import spock.lang.Specification

import java.util.function.Supplier

class MultiProcessSafeIndexedCacheTest extends Specification {
    final FileAccess fileAccess = Mock()
    final Supplier<BTreePersistentIndexedCache<String, String>> factory = Mock()
    final cache = new DefaultMultiProcessSafeIndexedCache<String, String>(factory, fileAccess)
    final BTreePersistentIndexedCache<String, String> backingCache = Mock()

    def "opens cache on first access"() {
        when:
        cache.getIfPresent("value")

        then:
        1 * fileAccess.writeFile(!null) >> { Runnable action -> action.run() }
        1 * factory.get() >> backingCache
    }

    def "holds read lock while getting entry from cache"() {
        given:
        cacheOpened()

        when:
        def result = cache.getIfPresent("value")

        then:
        result == "result"

        and:
        1 * fileAccess.readFile(!null) >> { Supplier action -> action.get() }
        1 * backingCache.get("value") >> "result"
        0 * _._
    }

    def "holds write lock while putting entry into cache"() {
        given:
        cacheOpened()

        when:
        cache.put("key", "value")

        then:
        1 * fileAccess.writeFile(!null) >> { Runnable action -> action.run() }
        1 * backingCache.put("key", "value")
        0 * _._
    }

    def "holds write lock while removing entry from cache"() {
        given:
        cacheOpened()

        when:
        cache.remove("key")

        then:
        1 * fileAccess.writeFile(!null) >> { Runnable action -> action.run() }
        1 * backingCache.remove("key")
        0 * _._
    }

    def "holds write lock while closing cache"() {
        given:
        cacheOpened()

        when:
        cache.finishWork()

        then:
        1 * fileAccess.writeFile(!null) >> { Runnable action -> action.run() }
        1 * backingCache.close()
        0 * _._
    }

    def "closes cache when closed"() {
        given:
        cacheOpened()

        when:
        cache.finishWork()

        then:
        1 * fileAccess.writeFile(!null) >> { Runnable action -> action.run() }
        1 * backingCache.close()
        0 * _._
    }

    def "does nothing on close when cache is not open"() {
        when:
        cache.finishWork()

        then:
        0 * _._
    }

    def "does nothing on close after cache already closed"() {
        cacheOpened()

        when:
        cache.finishWork()

        then:
        1 * fileAccess.writeFile(!null) >> { Runnable action -> action.run() }
        1 * backingCache.close()
        0 * _._

        when:
        cache.finishWork()

        then:
        0 * _._
    }

    def cacheOpened() {
        1 * fileAccess.writeFile(!null) >> { Runnable action -> action.run() }
        1 * factory.get() >> backingCache

        cache.getIfPresent("something")
    }
}
