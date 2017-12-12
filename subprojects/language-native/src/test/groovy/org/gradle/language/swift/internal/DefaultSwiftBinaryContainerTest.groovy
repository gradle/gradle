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

import org.gradle.api.Action
import org.gradle.api.internal.provider.DefaultProviderFactory
import org.gradle.api.specs.Spec
import org.gradle.language.swift.SwiftBinary
import spock.lang.Specification

class DefaultSwiftBinaryContainerTest extends Specification {
    def container = new DefaultSwiftBinaryContainer(new DefaultProviderFactory())

    def "can query elements when realized"() {
        def binary1 = Stub(SwiftBinary)
        def binary2 = Stub(SwiftBinary)

        given:
        container.add(binary1)
        container.add(binary2)

        when:
        container.realizeNow()

        then:
        container.get() == [binary1, binary2] as Set
    }

    def "cannot get elements before collection is realized"() {
        given:
        container.add(Stub(SwiftBinary))

        when:
        container.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot query the elements of this container as the elements have not been created yet.'
    }

    def "cannot get elements from when known action"() {
        given:
        container.whenElementKnown { container.get() }

        when:
        container.add(Stub(SwiftBinary))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot query the elements of this container as the elements have not been created yet.'
    }

    def "cannot get elements while collection is realizing"() {
        given:
        container.add(Stub(SwiftBinary))
        container.configureEach { container.get() }

        when:
        container.realizeNow()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot query the elements of this container as the elements have not been created yet.'
    }

    def "cannot add elements after collection is realized"() {
        given:
        container.add(Stub(SwiftBinary))
        container.realizeNow()

        when:
        container.add(Stub(SwiftBinary))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot add an element to this collection as it has already been realized.'
    }

    def "cannot add elements while collection is realizing"() {
        given:
        container.add(Stub(SwiftBinary))
        container.configureEach { container.add(Stub(SwiftBinary)) }

        when:
        container.realizeNow()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot add an element to this collection as it has already been realized.'
    }

    def "runs actions when collection is realized"() {
        def known = Mock(Action)
        def configure = Mock(Action)
        def finalized = Mock(Action)
        def binary1 = Stub(SwiftBinary)
        def binary2 = Stub(SwiftBinary)

        when:
        container.whenElementFinalized(finalized)
        container.configureEach(configure)
        container.whenElementKnown(known)
        container.add(binary1)
        container.add(binary2)

        then:
        1 * known.execute(binary1)
        1 * known.execute(binary2)
        0 * known._
        0 * configure._
        0 * finalized._

        when:
        container.realizeNow()

        then:
        1 * configure.execute(binary1)
        1 * configure.execute(binary2)
        0 * known._
        0 * configure._
        0 * finalized._

        then:
        1 * finalized.execute(binary1)
        1 * finalized.execute(binary2)
        0 * known._
        0 * configure._
        0 * finalized._
    }

    def "cannot add when known actions while collection is realizing"() {
        given:
        container.add(Stub(SwiftBinary))
        container.configureEach { container.whenElementKnown {} }

        when:
        container.realizeNow()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot add actions to this collection as it has already been realized.'
    }

    def "cannot add configure actions while collection is realizing"() {
        given:
        container.add(Stub(SwiftBinary))
        container.configureEach { container.configureEach {} }

        when:
        container.realizeNow()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot add actions to this collection as it has already been realized.'
    }

    def "cannot add when finalized actions while collection is realizing"() {
        given:
        container.add(Stub(SwiftBinary))
        container.configureEach { container.whenElementFinalized {} }

        when:
        container.realizeNow()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot add actions to this collection as it has already been realized.'
    }

    def "can get by name before element is present and query after realized"() {
        def binary1 = Stub(SwiftBinary)
        binary1.name >> "test1"

        expect:
        def p = container.getByName("test1")
        !p.present

        container.add(binary1)

        !p.present

        container.realizeNow()

        p.present
        p.get() == binary1
    }

    def "can get by name when element is already present"() {
        def binary1 = Stub(SwiftBinary)
        binary1.name >> "test1"

        expect:
        container.add(binary1)
        def p = container.getByName("test1")

        !p.present

        container.realizeNow()

        p.present
        p.get() == binary1
    }

    def "can get by name when element is already present and container realized"() {
        def binary1 = Stub(SwiftBinary)
        binary1.name >> "test1"

        expect:
        container.add(binary1)
        container.realizeNow()
        def p = container.getByName("test1")

        p.present
        p.get() == binary1
    }

    def "querying the result of get by name fails when not realized"() {
        def binary1 = Stub(SwiftBinary)
        binary1.name >> "test1"

        given:
        def p = container.getByName("test1")
        container.add(binary1)

        when:
        p.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'No value has been specified for this provider.'
    }

    def "querying the result of get by name fails when no element present"() {
        given:
        def p = container.getByName("test1")
        container.realizeNow()

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
        container.realizeNow()

        when:
        p.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Found multiple elements'
    }

    def "can get by spec before element is present and query after container realized"() {
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

        !p.present

        container.realizeNow()

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
        !p.present

        container.realizeNow()

        p.present
        p.get() == binary2
    }

    def "can get by spec when element is already present and container realized"() {
        def spec = Stub(Spec)
        def binary1 = Stub(SwiftBinary)
        def binary2 = Stub(SwiftBinary)

        spec.isSatisfiedBy(binary2) >> true

        container.add(binary1)
        container.add(binary2)
        container.realizeNow()

        expect:
        def p = container.get(spec)
        p.present
        p.get() == binary2
    }

    def "querying the result of get by spec fails when container is not realized"() {
        def spec = Stub(Spec)
        def binary = Stub(SwiftBinary)

        spec.isSatisfiedBy(binary) >> true

        given:
        def p = container.get(spec)
        container.add(binary)

        when:
        p.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'No value has been specified for this provider.'
    }

    def "querying the result of get by spec fails when no matching element present"() {
        def spec = Stub(Spec)
        spec.isSatisfiedBy(_) >> false
        def binary1 = Stub(SwiftBinary)

        given:
        def p = container.get(spec)
        container.add(binary1)
        container.realizeNow()

        when:
        p.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'No value has been specified for this provider.'
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
        container.realizeNow()

        when:
        p.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Found multiple elements'
    }

}
