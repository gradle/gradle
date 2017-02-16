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

package org.gradle.api.internal.cache

import org.gradle.api.Transformer
import org.gradle.initialization.SessionLifecycleListener
import org.gradle.internal.event.DefaultListenerManager
import spock.lang.Specification

class CrossBuildInMemoryCacheFactoryTest extends Specification {
    def listenerManager = new DefaultListenerManager()
    def factory = new CrossBuildInMemoryCacheFactory(listenerManager)

    def "creates a cache that uses the given transformer to create entries"() {
        def a = new Object()
        def b = new Object()
        def transformer = Mock(Transformer)

        given:
        transformer.transform("a") >> a
        transformer.transform("b") >> b

        def cache = factory.newCache()

        expect:
        cache.get("a", transformer) == a
        cache.get("b", transformer) == b
        cache.get("a", transformer) == a
    }

    def "creates each entry once"() {
        def a = new Object()
        def b = new Object()
        def c = new Object()
        def transformer = Mock(Transformer)

        given:
        def cache = factory.newCache()
        cache.put("c", c)

        when:
        def r1 = cache.get("a", transformer)
        def r2 = cache.get("b", transformer)
        def r3 = cache.get("a", transformer)
        def r4 = cache.get("b", transformer)
        def r5 = cache.get("c", transformer)

        then:
        r1 == a
        r2 == b
        r3 == a
        r4 == b
        r5 == c

        and:
        1 * transformer.transform("a") >> a
        1 * transformer.transform("b") >> b
        0 * transformer._
    }

    def "can get entries"() {
        def a = new Object()
        def b = new Object()
        def transformer = Mock(Transformer)

        given:
        transformer.transform("a") >> a

        def cache = factory.newCache()
        cache.get("a", transformer)
        cache.put("b", b)

        expect:
        cache.get("a") == a
        cache.get("b") == b
        cache.get("c") == null
    }

    def "retains strong references to values from the previous session"() {
        def transformer = Mock(Transformer)

        when:
        def cache = factory.newCache()
        cache.get("a", transformer)
        cache.get("b", transformer)

        then:
        1 * transformer.transform("a") >> new Object()
        1 * transformer.transform("b") >> new Object()
        0 * transformer._

        when:
        listenerManager.getBroadcaster(SessionLifecycleListener).beforeComplete()
        System.gc()
        cache.get("a", transformer)
        cache.get("b", transformer)

        then:
        0 * transformer._
    }
}
