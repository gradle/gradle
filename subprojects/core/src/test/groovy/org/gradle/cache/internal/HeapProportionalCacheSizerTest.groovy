/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

class HeapProportionalCacheSizerTest extends Specification {
    @Rule
    SetSystemProperties systemProperties = new SetSystemProperties()

    def "scaled value should be always larger than or equal to granularity"() {
        given:
        def heapProportionalCacheSizer = new HeapProportionalCacheSizer(1)
        when:
        def scaled = heapProportionalCacheSizer.scaleCacheSize(100, 100)
        then:
        scaled >= 100
    }

    def "cache cap sizer adjusts caps based on maximum heap size"() {
        given:
        def heapProportionalCacheSizer = new HeapProportionalCacheSizer(maxHeapMB)

        when:
        def caps = heapProportionalCacheSizer.scaleCacheSize(2000)

        then:
        caps == expectedCaps

        where:
        maxHeapMB | expectedCaps
        100       | 400
        200       | 400
        768       | 1600
        1024      | 2300
        1536      | 3600
        2048      | 4900
    }

    def "cache cap sizer honors reserved space when specified"() {
        given:
        System.setProperty(HeapProportionalCacheSizer.CACHE_RESERVED_SYSTEM_PROPERTY, reserved.toString())
        def heapProportionalCacheSizer = new HeapProportionalCacheSizer(maxHeapMB)

        when:
        def caps = heapProportionalCacheSizer.scaleCacheSize(2000)

        then:
        caps == expectedCaps

        where:
        maxHeapMB | reserved | expectedCaps
        100       | 50       | 400
        200       | 200      | 400
        968       | 200      | 1600
        1224      | 200      | 2300
        2036      | 500      | 3600
        4096      | 2048     | 4900
    }

}
