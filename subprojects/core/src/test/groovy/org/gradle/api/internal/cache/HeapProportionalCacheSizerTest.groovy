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

package org.gradle.api.internal.cache

import spock.lang.Specification


class HeapProportionalCacheSizerTest extends Specification {

    def "scaled value should be always larger than or equal to granularity"() {
        given:
        HeapProportionalCacheSizer heapProportionalCacheSizer = new HeapProportionalCacheSizer(1)
        when:
        def scaled = heapProportionalCacheSizer.scaleCacheSize(100, 100)
        then:
        scaled >= 100
    }
}
