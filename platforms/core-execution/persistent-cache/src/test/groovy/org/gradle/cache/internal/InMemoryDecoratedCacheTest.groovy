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

import com.google.common.cache.CacheBuilder
import org.gradle.cache.FileLock
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function

class InMemoryDecoratedCacheTest extends ConcurrentSpec {
    def target = Mock(MultiProcessSafeAsyncPersistentIndexedCache)
    def cache = new InMemoryDecoratedCache(target, CacheBuilder.newBuilder().build(), "id", new AtomicReference<FileLock.State>())

    def "does not produce value when present in memory and marks completed"() {
        def producer = Mock(Function)
        def completion = Mock(Runnable)

        given:
        cache.putLater("key", "value", Stub(Runnable))

        when:
        def result = cache.get("key", producer, completion)

        then:
        result == "value"

        and:
        1 * completion.run()
        0 * _
    }

    def "does not produce value when present in backing cache and marks completed"() {
        def producer = Mock(Function)
        def completion = Mock(Runnable)

        when:
        def result = cache.get("key", producer, completion)

        then:
        result == "value"

        and:
        1 * target.get("key") >> "value"
        1 * completion.run()
        0 * _
    }

    def "produces value and stores in backing cache later when not present"() {
        def producer = Mock(Function)
        def completion = Mock(Runnable)

        when:
        def result = cache.get("key", producer, completion)

        then:
        result == "value"

        and:
        1 * target.get("key") >> null
        1 * producer.apply("key") >> "value"
        1 * target.putLater("key", "value", completion)
        0 * _

        when:
        def cached = cache.get("key")

        then:
        cached == "value"
        0 * _
    }

    def "produces value and stores in backing cache later after being removed"() {
        def producer = Mock(Function)
        def completion = Mock(Runnable)

        given:
        cache.removeLater("key", Stub(Runnable))

        when:
        def result = cache.get("key", producer, completion)

        then:
        result == "value"

        and:
        1 * producer.apply("key") >> "value"
        1 * target.putLater("key", "value", completion)
        0 * _

        when:
        def cached = cache.get("key")

        then:
        cached == "value"
        0 * _
    }

    def "propagates failure to produce value and marks completed"() {
        def producer = Mock(Function)
        def completion = Mock(Runnable)
        def failure = new RuntimeException()

        when:
        cache.get("key", producer, completion)

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        1 * target.get("key") >> null
        1 * producer.apply("key") >> { throw failure }
        1 * completion.run()
        0 * _
    }

    def "produces value once when requested from multiple threads"() {
        def producer = Mock(Function)

        when:
        def result1
        def result2
        def result3
        def result4
        async {
            start {
                result1 = cache.get("key", producer, Stub(Runnable))
            }
            start {
                result2 = cache.get("key", producer, Stub(Runnable))
            }
            start {
                result3 = cache.get("key", producer, Stub(Runnable))
            }
            start {
                result4 = cache.get("key", producer, Stub(Runnable))
            }
        }

        then:
        result1 == "result"
        result2 == "result"
        result3 == "result"
        result4 == "result"

        and:
        1 * target.get("key") >> null
        1 * producer.apply("key") >> "result"
        1 * target.putLater("key", "result", _)
        0 * producer._
        0 * target._
    }

    def "multiple threads can produce different entries concurrently"() {
        when:
        async {
            start {
                cache.get("key1", {
                    instant.one
                    thread.block()
                    instant.one_done
                    return "one"
                }, Stub(Runnable))
            }
            start {
                cache.get("key2", {
                    instant.two
                    thread.block()
                    instant.two_done
                    return "two"
                }, Stub(Runnable))
            }
        }

        then:
        instant.one_done > instant.two
        instant.two_done > instant.one
    }
}
