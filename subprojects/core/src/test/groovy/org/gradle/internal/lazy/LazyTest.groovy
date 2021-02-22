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

package org.gradle.internal.lazy

import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

class LazyTest extends Specification {

    @Unroll
    def "supplier code is executed once"() {
        def supplier = Mock(Supplier)

        when:
        def lazy = factory(supplier)

        then:
        0 * supplier._

        when:
        lazy.get()

        then:
        1 * supplier.get() >> 123

        when:
        lazy.get()

        then:
        0 * supplier.get()

        when:
        lazy.use {
            assert it == expected
        }

        then:
        noExceptionThrown()

        when:
        def val = lazy.apply {
            3 * it
        }

        then:
        0 * supplier.get()
        val == 3 * expected

        where:
        factory                                                  | expected
        asClosure { s -> Lazy.unsafe().of(s as Supplier) }                 | 123
        asClosure { s -> Lazy.unsafe().of(s as Supplier).map { 2 * it } }  | 246
        asClosure { s -> Lazy.locking().of(s as Supplier) }                | 123
        asClosure { s -> Lazy.locking().of(s as Supplier).map { 2 * it } } | 246
    }

    @Unroll
    def "lazy can handle concurrent threads (#factoryName)"() {
        def supplier = Mock(Supplier)
        def lazy = factory.of(supplier)
        def executors = Executors.newFixedThreadPool(20)

        when:
        50.times {
            executors.submit {
                assert lazy.get() == 'hello'
            }
        }
        executors.shutdown()
        executors.awaitTermination(1, TimeUnit.MINUTES)

        then:
        1 * supplier.get() >> 'hello'

        where:
        factoryName     | factory
        'locking'       | Lazy.locking()
        'synchronized'  | Lazy.synchronizing()
    }

    @Unroll
    def "locking lazy can handle concurrent threads (#factoryName)"() {
        def supplier = Mock(Supplier)
        def lazy = factory.of(supplier).map { 2 * it }
        def executors = Executors.newFixedThreadPool(20)

        when:
        50.times {
            executors.submit {
                lazy.get()
            }
        }
        executors.shutdown()
        executors.awaitTermination(1, TimeUnit.MINUTES)

        then:
        1 * supplier.get()

        where:
        factoryName     | factory
        'locking'       | Lazy.locking()
        'synchronized'  | Lazy.synchronizing()
    }

    Closure asClosure(Closure<Lazy<Object>> lazyClosure) {
        return lazyClosure
    }
}
