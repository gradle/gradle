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

package org.gradle.language.swift.internal

import org.gradle.api.internal.provider.DefaultProviderFactory
import org.gradle.api.specs.Spec
import org.gradle.language.swift.SwiftBinary
import spock.lang.Specification

class DefaultSwiftBinaryContainerTest extends Specification {
    def container = new DefaultSwiftBinaryContainer(new DefaultProviderFactory())

    def "can get by name before element is present"() {
        def binary1 = Stub(SwiftBinary)
        binary1.name >> "test1"

        expect:
        def p = container.getByName("test1")
        !p.present

        container.add(binary1)

        p.present
        p.get() == binary1
    }

    def "can get by name when element is already present"() {
        def binary1 = Stub(SwiftBinary)
        binary1.name >> "test1"

        expect:
        def p = container.getByName("test1")
        container.add(binary1)

        p.present
        p.get() == binary1
    }

    def "querying the result of get by name fails when no element present"() {
        given:
        def p = container.getByName("test1")

        when:
        p.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'No value has been specified for this provider.'
    }

    def "querying the result of get by name fails when multiple elements present"() {
        def binary1 = Stub(SwiftBinary)
        binary1.name >> "test1"
        def binary2 = Stub(SwiftBinary)
        binary2.name >> "test1"

        given:
        def p = container.getByName("test1")
        container.add(binary1)
        container.add(binary2)

        when:
        p.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Found multiple elements'
    }

    def "can get by spec before element is present"() {
        def spec = Stub(Spec)
        def binary1 = Stub(SwiftBinary)
        def binary2 = Stub(SwiftBinary)

        spec.isSatisfiedBy(binary2) >> true

        expect:
        def p = container.get(spec)
        !p.present

        container.add(binary1)

        !p.present

        container.add(binary2)

        p.present
        p.get() == binary2
    }

    def "can get by spec when element is already present"() {
        def spec = Stub(Spec)
        def binary1 = Stub(SwiftBinary)
        def binary2 = Stub(SwiftBinary)

        spec.isSatisfiedBy(binary2) >> true

        container.add(binary1)
        container.add(binary2)

        expect:
        def p = container.get(spec)
        p.present
        p.get() == binary2
    }

    def "querying the result of get by spec fails when no matching element present"() {
        def spec = Stub(Spec)
        spec.isSatisfiedBy(_) >> false
        def binary1 = Stub(SwiftBinary)

        given:
        def p = container.get(spec)

        when:
        p.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'No value has been specified for this provider.'

        when:
        container.add(binary1)
        p.get()

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'No value has been specified for this provider.'
    }

    def "querying the result of get by spec fails when multiple matching elements present"() {
        def binary1 = Stub(SwiftBinary)
        def binary2 = Stub(SwiftBinary)
        def spec = Stub(Spec)
        spec.isSatisfiedBy(_) >> true

        given:
        def p = container.get(spec)
        container.add(binary1)
        container.add(binary2)

        when:
        p.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Found multiple elements'
    }

}
