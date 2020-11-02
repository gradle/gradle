/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.initialization.SessionLifecycleListener
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.service.scopes.Scopes
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Function

class DefaultCrossBuildInMemoryCacheFactoryTest extends ConcurrentSpec {
    def listenerManager = new DefaultListenerManager(Scopes.BuildSession)
    def factory = new DefaultCrossBuildInMemoryCacheFactory(listenerManager)

    def "creates a cache that uses the given transformer to create entries"() {
        def a = new Object()
        def b = new Object()
        def function = Mock(Function)

        given:
        function.apply("a") >> a
        function.apply("b") >> b

        def cache = factory.newCache()

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
        def cache = factory.newCache()
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
        def cache = factory.newCache()

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

        def cache = factory.newCache()
        cache.get("a", function)
        cache.put("b", b)

        expect:
        cache.get("a") == a
        cache.get("b") == b
        cache.get("c") == null
    }

    def "retains strong references to values from the previous session"() {
        def function = Mock(Function)

        when:
        def cache = factory.newCache()
        cache.get("a", function)
        cache.get("b", function)

        then:
        1 * function.apply("a") >> new Object()
        1 * function.apply("b") >> new Object()
        0 * function._

        when:
        listenerManager.getBroadcaster(SessionLifecycleListener).beforeComplete()
        System.gc()
        cache.get("a", function)
        cache.get("b", function)

        then:
        0 * function._
    }

    def "creates a cache whose keys are classes"() {
        def a = new Object()
        def b = new Object()
        def c = new Object()
        def function = Mock(Function)

        given:
        function.apply(String) >> a
        function.apply(Long) >> b

        def cache = factory.newClassCache()

        expect:
        cache.get(String, function) == a
        cache.get(Long, function) == b
        cache.get(String) == a
        cache.get(Long) == b

        cache.put(String, c)
        cache.get(String) == c

        cache.clear()
        cache.get(String) == null
    }

    def "creates a map whose keys are classes"() {
        def a = new Object()
        def b = new Object()
        def c = new Object()
        def function = Mock(Function)

        given:
        function.apply(String) >> a
        function.apply(Long) >> b

        def cache = factory.newClassMap()

        expect:
        cache.get(String, function) == a
        cache.get(Long, function) == b
        cache.get(String) == a
        cache.get(Long) == b

        cache.put(String, c)
        cache.get(String) == c
    }
}
