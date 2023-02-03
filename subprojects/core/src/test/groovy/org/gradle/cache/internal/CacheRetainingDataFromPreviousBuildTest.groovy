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

import org.gradle.internal.session.BuildSessionLifecycleListener

import java.util.function.Function

class CacheRetainingDataFromPreviousBuildTest extends AbstractCrossBuildInMemoryCacheTest<String> {

    @Override
    CrossBuildInMemoryCache<String, Object> newCache() {
        return factory.newCacheRetainingDataFromPreviousBuild { value -> true }
    }

    def "retains values from the previous session"() {
        def function = Mock(Function)

        when:
        def cache = newCache()
        cache.get("a", function)
        cache.get("b", function)
        cache.get("c", function)

        then:
        1 * function.apply("a") >> new Object()
        1 * function.apply("b") >> new Object()
        1 * function.apply("c") >> new Object()
        0 * function._

        when:
        listenerManager.getBroadcaster(BuildSessionLifecycleListener).beforeComplete()
        cache.get("a", function)
        cache.get("b", function)
        // Do not refresh "c"

        then:
        0 * function._

        when:
        listenerManager.getBroadcaster(BuildSessionLifecycleListener).beforeComplete()
        cache.get("a", function)
        // Do not refresh "b" or "c"
        then:
        0 * function._

        when:
        listenerManager.getBroadcaster(BuildSessionLifecycleListener).beforeComplete()
        cache.get("a", function)
        cache.get("b", function)
        cache.get("c", function)
        then:
        1 * function.apply("c") >> new Object()
        0 * function._
    }

    def "does not retain values from the previous session which are not to be kept"() {
        def function = Mock(Function)
        def notToBeKept = new Object()

        when:
        def cache = factory.newCacheRetainingDataFromPreviousBuild { value -> value != notToBeKept }
        cache.get("a", function)
        cache.get("b", function)
        cache.get("c", function)

        then:
        1 * function.apply("a") >> new Object()
        1 * function.apply("b") >> notToBeKept
        1 * function.apply("c") >> notToBeKept
        0 * function._

        when:
        listenerManager.getBroadcaster(BuildSessionLifecycleListener).beforeComplete()
        cache.get("a", function)
        cache.get("b", function)
        cache.get("c", function)

        then:
        1 * function.apply("b") >> new Object()
        1 * function.apply("c") >> notToBeKept
        0 * function._

        when:
        listenerManager.getBroadcaster(BuildSessionLifecycleListener).beforeComplete()
        cache.get("a", function)
        cache.get("b", function)
        cache.get("c", function)
        then:
        1 * function.apply("c") >> new Object()
        0 * function._
    }
}
