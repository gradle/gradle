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

package org.gradle.internal.id

import spock.lang.Specification

class UniqueIdTest extends Specification {

    def "generates unique values"() {
        expect:
        def n = 100
        (1..n).collect { UniqueId.generate() }.unique().size() == n
    }

    def "can roundtrip to / from string"() {
        when:
        def id = UniqueId.generate()
        def s = id.asString()

        then:
        UniqueId.from(s) == id
        UniqueId.from(s).asString() == s
    }

}
