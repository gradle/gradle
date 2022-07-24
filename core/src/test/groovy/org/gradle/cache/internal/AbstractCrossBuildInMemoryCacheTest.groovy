/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.service.scopes.Scopes
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Function

abstract class AbstractCrossBuildInMemoryCacheTest<K> extends ConcurrentSpec {
    def listenerManager = new DefaultListenerManager(Scopes.BuildSession)
    def factory = new DefaultCrossBuildInMemoryCacheFactory(listenerManager)

    abstract CrossBuildInMemoryCache<String, Object> newCache()

    def "creates a cache that uses the given function to create entries"() {
        def a = new Object()
        def b = new Object()
        def function = Mock(Function)

        given:
        function.apply("a") >> a
        function.apply("b") >> b

        def cache = newCache()

        expect:
        cache.get("a", function) == a
        cache.get("b", function) == b
        cache.get("a", function) == a
    }

    def "creates each entry once"() {
        def a = new Object()
        def b = new Object()
        def c = new Object()
        def function = Mock(Function)

        given:
        def cache = newCache()
        cache.put("c", c)

        when:
        def r1 = cache.get("a", function)
        def r2 = cache.get("b", function)
        def r3 = cache.get("a", function)
        def r4 = cache.get("b", function)
        def r5 = cache.get("c", function)

        then:
        r1 == a
        r2 == b
        r3 == a
        r4 == b
        r5 == c

        and:
        1 * function.apply("a") >> a
        1 * function.apply("b") >> b
        0 * function._
    }

    def "entry is created once when multiple threads attempt to create the same entry"() {
        def a = new Object()
        def function = Mock(Function)

        given:
        def cache = newCache()

        when:
        def values = new CopyOnWriteArrayList()
        async {
            start {
                values << cache.get("a", function)
            }
            start {
                values << cache.get("a", function)
            }
            start {
                values << cache.get("a", function)
            }
            start {
                values << cache.get("a", function)
            }
        }

        then:
        values.unique() == [a]

        and:
        1 * function.apply("a") >> a
        0 * function._
    }

    def "can get entries"() {
        def a = new Object()
        def b = new Object()
        def function = Mock(Function)

        given:
        function.apply("a") >> a

        def cache = newCache()
        cache.get("a", function)
        cache.put("b", b)

        expect:
        cache.getIfPresent("a") == a
        cache.getIfPresent("b") == b
        cache.getIfPresent("c") == null
    }
}
