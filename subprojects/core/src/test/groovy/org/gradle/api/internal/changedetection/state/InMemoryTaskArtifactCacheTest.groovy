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

package org.gradle.api.internal.changedetection.state

import org.gradle.cache.internal.AsyncCacheAccess
import org.gradle.cache.internal.MultiProcessSafePersistentIndexedCache
import spock.lang.Specification

import java.util.concurrent.Callable

class InMemoryTaskArtifactCacheTest extends Specification {
    def cacheFactory = new InMemoryTaskArtifactCache()
    def target = Mock(MultiProcessSafePersistentIndexedCache)
    def asyncCacheAccess = Mock(AsyncCacheAccess)

    def "caches result from backing cache"() {
        given:
        def cache = cacheFactory.decorate("path/fileSnapshots.bin", "fileSnapshots", target, asyncCacheAccess)

        when:
        def result = cache.get("key")

        then:
        result == "result"

        and:
        1 * asyncCacheAccess.read(_) >> { Callable task -> task.call() }
        1 * target.get("key") >> "result"
        0 * target._

        when:
        result = cache.get("key")

        then:
        result == "result"

        and:
        0 * target._
    }

    def "caches null result from backing cache"() {
        given:
        def cache = cacheFactory.decorate("path/fileSnapshots.bin", "fileSnapshots", target, asyncCacheAccess)

        when:
        def result = cache.get("key")

        then:
        result == null

        and:
        1 * asyncCacheAccess.read(_) >> { Callable task -> task.call() }
        1 * target.get("key") >> null
        0 * target._

        when:
        result = cache.get("key")

        then:
        result == null

        and:
        0 * target._
    }

    def "caches result of putting item"() {
        given:
        def cache = cacheFactory.decorate("path/fileSnapshots.bin", "fileSnapshots", target, asyncCacheAccess)

        when:
        def result = cache.get("key")

        then:
        result == "result"

        and:
        1 * asyncCacheAccess.read(_) >> { Callable task -> task.call() }
        1 * target.get("key") >> "result"
        0 * target._

        when:
        cache.put("key", "new value")
        Thread.sleep(100L)

        then:
        1 * asyncCacheAccess.enqueue(_) >> { Runnable action -> action.run() }
        1 * target.put("key", "new value")
        0 * _._

        when:
        result = cache.get("key")

        then:
        result == "new value"

        and:
        0 * target._
    }

    def "caches result of removing item"() {
        given:
        def cache = cacheFactory.decorate("path/fileSnapshots.bin", "fileSnapshots", target, asyncCacheAccess)

        when:
        def result = cache.get("key")

        then:
        result == "result"

        and:
        1 * asyncCacheAccess.read(_) >> { Callable task -> task.call() }
        1 * target.get("key") >> "result"
        0 * target._

        when:
        cache.remove("key")
        Thread.sleep(100L)

        then:
        1 * asyncCacheAccess.enqueue(_) >> { Runnable action -> action.run() }
        1 * target.remove("key")
        0 * _._

        when:
        result = cache.get("key")

        then:
        result == null

        and:
        0 * target._
    }

}
