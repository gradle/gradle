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

import org.gradle.internal.session.BuildSessionLifecycleListener

import java.util.function.Function

class DefaultCrossBuildInMemoryCacheFactoryTest extends AbstractCrossBuildInMemoryCacheTest {

    @Override
    CrossBuildInMemoryCache<String, Object> newCache() {
        return factory.newCache()
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
        listenerManager.getBroadcaster(BuildSessionLifecycleListener).beforeComplete()
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
        cache.getIfPresent(String) == a
        cache.getIfPresent(Long) == b

        cache.put(String, c)
        cache.getIfPresent(String) == c

        cache.clear()
        cache.getIfPresent(String) == null
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
        cache.getIfPresent(String) == a
        cache.getIfPresent(Long) == b

        cache.put(String, c)
        cache.getIfPresent(String) == c
    }
}
