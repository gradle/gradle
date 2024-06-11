/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.cache.AsyncCacheAccess
import org.gradle.cache.CrossProcessCacheAccess
import org.gradle.cache.MultiProcessSafeIndexedCache
import spock.lang.Specification

import java.util.function.Supplier

class InMemoryCacheDecoratorFactoryTest extends Specification {
    def cacheFactory = new DefaultInMemoryCacheDecoratorFactory(false, new TestCrossBuildInMemoryCacheFactory())
    def target = Mock(MultiProcessSafeIndexedCache)
    def asyncCacheAccess = Mock(AsyncCacheAccess)
    def crossProcessCacheAccess = Mock(CrossProcessCacheAccess)

    def "caches result from backing cache and reuses for other instances with the same cache id"() {
        given:
        def cache = cacheFactory.decorator(100, true).decorate("path/fileSnapshots.bin", "fileSnapshots", target, crossProcessCacheAccess, asyncCacheAccess)

        when:
        def result = cache.getIfPresent("key")

        then:
        result == "result"

        and:
        1 * crossProcessCacheAccess.withFileLock(_) >> { Supplier task -> task.get() }
        1 * asyncCacheAccess.read(_) >> { Supplier task -> task.get() }
        1 * target.getIfPresent("key") >> "result"
        0 * target._

        when:
        result = cache.getIfPresent("key")

        then:
        result == "result"

        and:
        1 * crossProcessCacheAccess.withFileLock(_) >> { Supplier task -> task.get() }
        0 * target._

        when:
        def cache2 = cacheFactory.decorator(100, true).decorate("path/fileSnapshots.bin", "fileSnapshots", target, crossProcessCacheAccess, asyncCacheAccess)
        result = cache2.getIfPresent("key")

        then:
        result == "result"

        and:
        1 * crossProcessCacheAccess.withFileLock(_) >> { Supplier task -> task.get() }
        0 * target._
    }

    def "does not cache result when not long running process"() {
        given:
        def cache = cacheFactory.decorator(100, false).decorate("path/fileSnapshots.bin", "fileSnapshots", target, crossProcessCacheAccess, asyncCacheAccess)

        when:
        def result = cache.getIfPresent("key")

        then:
        result == "result 1"

        and:
        1 * crossProcessCacheAccess.withFileLock(_) >> { Supplier task -> task.get() }
        1 * asyncCacheAccess.read(_) >> { Supplier task -> task.get() }
        1 * target.getIfPresent("key") >> "result 1"
        0 * target._

        when:
        result = cache.getIfPresent("key")

        then:
        result == "result 2"

        and:
        1 * crossProcessCacheAccess.withFileLock(_) >> { Supplier task -> task.get() }
        1 * asyncCacheAccess.read(_) >> { Supplier task -> task.get() }
        1 * target.getIfPresent("key") >> "result 2"
        0 * target._
    }

    def "caches null result from backing cache"() {
        given:
        def cache = cacheFactory.decorator(100, true).decorate("path/fileSnapshots.bin", "fileSnapshots", target, crossProcessCacheAccess, asyncCacheAccess)

        when:
        def result = cache.getIfPresent("key")

        then:
        result == null

        and:
        1 * crossProcessCacheAccess.withFileLock(_) >> { Supplier task -> task.get() }
        1 * asyncCacheAccess.read(_) >> { Supplier task -> task.get() }
        1 * target.getIfPresent("key") >> null
        0 * target._

        when:
        result = cache.getIfPresent("key")

        then:
        result == null

        and:
        1 * crossProcessCacheAccess.withFileLock(_) >> { Supplier task -> task.get() }
        0 * target._
    }

    def "caches result of putting item"() {
        def lock = Mock(Runnable)

        given:
        def cache = cacheFactory.decorator(100, true).decorate("path/fileSnapshots.bin", "fileSnapshots", target, crossProcessCacheAccess, asyncCacheAccess)

        when:
        def result = cache.getIfPresent("key")

        then:
        result == "result"

        and:
        1 * crossProcessCacheAccess.withFileLock(_) >> { Supplier task -> task.get() }
        1 * asyncCacheAccess.read(_) >> { Supplier task -> task.get() }
        1 * target.getIfPresent("key") >> "result"
        0 * target._

        when:
        cache.put("key", "new value")

        then:
        1 * crossProcessCacheAccess.acquireFileLock() >> lock
        1 * asyncCacheAccess.enqueue(_) >> { Runnable action -> action.run() }
        1 * target.put("key", "new value")
        1 * lock.run()
        0 * _._

        when:
        result = cache.getIfPresent("key")

        then:
        result == "new value"

        and:
        1 * crossProcessCacheAccess.withFileLock(_) >> { Supplier task -> task.get() }
        0 * target._
    }

    def "caches result of removing item"() {
        def lock = Mock(Runnable)

        given:
        def cache = cacheFactory.decorator(100, true).decorate("path/fileSnapshots.bin", "fileSnapshots", target, crossProcessCacheAccess, asyncCacheAccess)

        when:
        def result = cache.getIfPresent("key")

        then:
        result == "result"

        and:
        1 * crossProcessCacheAccess.withFileLock(_) >> { Supplier task -> task.get() }
        1 * asyncCacheAccess.read(_) >> { Supplier task -> task.get() }
        1 * target.getIfPresent("key") >> "result"
        0 * target._

        when:
        cache.remove("key")

        then:
        1 * crossProcessCacheAccess.acquireFileLock() >> lock
        1 * asyncCacheAccess.enqueue(_) >> { Runnable action -> action.run() }
        1 * target.remove("key")
        1 * lock.run()
        0 * _._

        when:
        result = cache.getIfPresent("key")

        then:
        result == null

        and:
        1 * crossProcessCacheAccess.withFileLock(_) >> { Supplier task -> task.get() }
        0 * target._
    }

}
