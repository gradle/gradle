/*
 * Copyright 2011 the original author or authors.
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

import spock.lang.Specification

import java.util.function.Supplier

class MapBackedCacheTest extends Specification {

    def cache(map = [:]) {
        new MapBackedCache(map)
    }

    def "has cache semantics"() {
        given:
        def map = [:]
        def cache = cache(map)

        expect:
        cache.get("a", { 1 } as Supplier) == 1
        cache.get("a", { 2 } as Supplier) == 1

        and:
        map.a == 1

        when:
        map.clear()

        then:
        cache.get("a", { 2 } as Supplier) == 2
    }

}
