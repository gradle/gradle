/*
 * Copyright 2018 the original author or authors.
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

class CacheVersionTest extends Specification {

    def "parses version number"() {
        expect:
        CacheVersion.parse("") == CacheVersion.empty()
        CacheVersion.parse("42") == CacheVersion.of(42)
        CacheVersion.parse("1.2.3.4.5") == CacheVersion.of(1, 2, 3, 4, 5)
    }

    def "compares versions"() {
        expect:
        CacheVersion.of(2) > CacheVersion.of(1)
        CacheVersion.of(1) < CacheVersion.of(2)
        CacheVersion.of(1) == CacheVersion.of(1)

        and:
        CacheVersion.of(1, 1) > CacheVersion.of(1)
        CacheVersion.of(1) < CacheVersion.of(1, 1)

        and:
        CacheVersion.of(1, 2) > CacheVersion.of(1, 1)
        CacheVersion.of(1, 1) < CacheVersion.of(1, 2)

        and:
        CacheVersion.of(1, 0) > CacheVersion.of(1)
        CacheVersion.of(1) < CacheVersion.of(1, 0)

        and:
        CacheVersion.of(2) > CacheVersion.of(1, 1)
        CacheVersion.of(1, 1) < CacheVersion.of(2)
    }

    def "appends component"() {
        expect:
        CacheVersion.empty().append(42) == CacheVersion.of(42)
        CacheVersion.of(42).append(23) == CacheVersion.of(42, 23)
        CacheVersion.of(42, 23).append(14) == CacheVersion.of(42, 23, 14)
    }

    def "copies input array"() {
        given:
        int[] components = [ 23, 42 ]
        def version = CacheVersion.of(components)

        when:
        components[0] = 0
        components[1] = 1

        then:
        version == CacheVersion.of(23, 42)
    }

    def "returns new instance when appending"() {
        given:
        def version = CacheVersion.of(1)

        when:
        def newVersion = version.append(2)

        then:
        newVersion != version
        version == CacheVersion.of(1)
    }
}
