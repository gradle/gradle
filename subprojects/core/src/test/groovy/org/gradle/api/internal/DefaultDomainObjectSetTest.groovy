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
package org.gradle.api.internal

import spock.lang.Specification

class DefaultDomainObjectSetTest extends Specification {
    def "findAll() filters elements and retains iteration order"() {
        def set = new DefaultDomainObjectSet<String>(String)
        set.add("a")
        set.add("b")
        set.add("c")
        set.add("d")

        expect:
        set.findAll { it != "c" } == ["a", "b", "d"] as LinkedHashSet
    }

    def "Set semantics preserved if backing collection is a filtered composite set"() {
        def c1 = new DefaultDomainObjectSet<String>(String)
        def c2 = new DefaultDomainObjectSet<String>(String)
        given:
        def composite = CompositeDomainObjectSet.<String>create(String, c1, c2)
        def set = new DefaultDomainObjectSet<String>(String, composite.withType(String))

        when:
        c1.add("a")
        c1.add("b")
        c1.add("c")
        c1.add("d")
        c2.add("a")
        c2.add("c")

        then:
        set.size() == 4
        set.findAll { it != "c" } == ["a", "b", "d"] as LinkedHashSet
        set.iterator().collect { it } == ["a", "b", "c", "d"]
    }
}
