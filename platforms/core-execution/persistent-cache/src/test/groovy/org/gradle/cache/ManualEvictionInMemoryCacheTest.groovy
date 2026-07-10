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

package org.gradle.cache

import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

class ManualEvictionInMemoryCacheTest extends Specification {

    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    def "supports #concurrency concurrent computations"() {
        def latch = new CountDownLatch(concurrency)
        def executor = Executors.newFixedThreadPool(concurrency)
        def cache = new ManualEvictionInMemoryCache<String, String>()

        when:
        concurrency.times { idx ->
            executor.submit {
                String key = "key${idx}"
                cache.get(key, {
                    println "Waiting for key ${key}"
                    latch.countDown()
                    println "Waiting for key ${key}"
                    latch.await()
                    println "Created key ${key}"
                    key
                } as Supplier<String>)
            }
        }
        executor.shutdown()
        executor.awaitTermination(2, TimeUnit.SECONDS)
        then:
        executor.isTerminated()
        concurrency.times {
            String key = "key${it}"
            assert cache.getIfPresent(key) == key
        }

        where:
        concurrency << [8, 16, 32]
    }
}
