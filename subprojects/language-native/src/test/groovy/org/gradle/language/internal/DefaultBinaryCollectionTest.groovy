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

package org.gradle.language.internal

import org.gradle.api.Action
import org.gradle.api.specs.Spec
import org.gradle.language.cpp.CppBinary
import org.gradle.language.swift.SwiftBinary
import org.gradle.language.swift.SwiftSharedLibrary
import spock.lang.Specification

class DefaultBinaryCollectionTest extends Specification {
    def container = new DefaultBinaryCollection(SwiftBinary)

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
        container.add(Stub(SwiftBinary))
        container.whenElementKnown { container.get() }

        when:
        container.realizeNow()

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

    def "runs actions only when collection is realized"() {
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
        0 * known._
        0 * configure._
        0 * finalized._

        when:
        container.realizeNow()

        then:
        1 * known.execute(binary1)
        1 * known.execute(binary2)
        0 * known._
        0 * configure._
        0 * finalized._

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

    def "runs actions on element of given type when collection is realized"() {
        def known = Mock(Action)
        def configure = Mock(Action)
        def finalized = Mock(Action)
        def binary1 = Stub(CppBinary)
        def binary2 = Stub(SwiftBinary)

        when:
        container.whenElementFinalized(SwiftBinary, finalized)
        container.configureEach(SwiftBinary, configure)
        container.whenElementKnown(SwiftBinary, known)
        container.add(binary1)
        container.add(binary2)

        then:
        0 * known._
        0 * configure._
        0 * finalized._

        when:
        container.realizeNow()

        then:
        1 * known.execute(binary2)
        0 * known._
        0 * configure._
        0 * finalized._

        then:
        1 * configure.execute(binary2)
        0 * known._
        0 * configure._
        0 * finalized._

        then:
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

    def "can add when finalized actions while collection is realizing"() {
        given:
        def action = Mock(Action)
        def binary = Stub(SwiftBinary)
        container.add(binary)
        container.configureEach { container.whenElementFinalized(action) }

        when:
        container.realizeNow()

        then:
        1 * action.execute(binary)
        0 * action._
    }

    def "executes when finalized actions eagerly when collection is finalized"() {
        given:
        def action = Mock(Action)
        def binary = Stub(SwiftBinary)
        container.add(binary)
        container.realizeNow()

        when:
        container.whenElementFinalized(action)

        then:
        1 * action.execute(binary)
        0 * action._
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
        e.message == 'Cannot query the value of this provider because it has no value available.'
    }

    def "querying the result of get by name fails when no element present"() {
        given:
        def p = container.getByName("test1")
        container.realizeNow()

        when:
        p.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot query the value of this provider because it has no value available.'
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

    def "can configure by name before element is realized"() {
        def action = Mock(Action)
        def binary1 = Stub(SwiftBinary)
        binary1.name >> "test1"
        def binary2 = Stub(SwiftBinary)
        binary2.name >> "test2"

        when:
        def p = container.getByName("test1")
        p.configure(action)

        container.add(binary1)
        container.add(binary2)

        then:
        0 * action._

        when:
        container.realizeNow()

        then:
        1 * action.execute(binary1)
        0 * action._
    }

    def "does not invoke configuration by name action when there is no match"() {
        def action = Mock(Action)
        def binary2 = Stub(SwiftBinary)
        binary2.name >> "test2"

        when:
        def p = container.getByName("test1")
        p.configure(action)

        container.add(binary2)

        then:
        0 * action._

        when:
        container.realizeNow()

        then:
        0 * action._
    }

    def "cannot add configure by name action after container is realized"() {
        def binary = Stub(SwiftBinary)
        binary.name >> 'test1'

        given:
        container.add(binary)
        def p = container.getByName('test1')
        container.realizeNow()

        when:
        p.configure { }

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot add actions to this collection as it has already been realized.'
    }

    def "fires finalize event for element with name after configuration has completed"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)
        def finalAction = Mock(Action)
        def binary1 = Stub(SwiftBinary)
        binary1.name >> "test1"
        def binary2 = Stub(SwiftBinary)
        binary2.name >> "test2"

        when:
        def p = container.getByName("test1")
        p.whenFinalized(finalAction)
        p.configure(action1)
        container.configureEach(action2)

        container.add(binary1)
        container.add(binary2)

        then:
        0 * _

        when:
        container.realizeNow()

        then:
        1 * action1.execute(binary1)
        1 * action2.execute(binary1)
        1 * action2.execute(binary2)
        0 * _

        then:
        1 * finalAction.execute(binary1)
        0 * _
    }

    def "can receive finalize event for element with name after configuration has completed"() {
        def binary1 = Stub(SwiftBinary)
        binary1.name >> "test1"
        def binary2 = Stub(SwiftBinary)
        binary2.name >> "test2"
        def finalAction = Mock(Action)

        given:
        def p = container.getByName("test1")
        container.add(binary1)
        container.add(binary2)
        container.realizeNow()

        when:
        p.whenFinalized(finalAction)

        then:
        1 * finalAction.execute(binary1)
        0 * _
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

    def "fires finalize event for element by spec after configuration has completed"() {
        def spec = Stub(Spec)
        def binary1 = Stub(SwiftBinary)
        def binary2 = Stub(SwiftBinary)
        def action1 = Mock(Action)
        def action2 = Mock(Action)
        def finalizeAction = Mock(Action)

        spec.isSatisfiedBy(binary2) >> true

        given:
        def p = container.get(spec)
        p.whenFinalized(finalizeAction)
        p.configure(action1)
        container.configureEach(action2)
        container.add(binary1)
        container.add(binary2)

        when:
        container.realizeNow()

        then:
        1 * action1.execute(binary2)
        1 * action2.execute(binary1)
        1 * action2.execute(binary2)
        0 * _

        then:
        1 * finalizeAction.execute(binary2)
        0 * _
    }

    def "reuses the result of matching by spec"() {
        def spec = Mock(Spec)
        def binary1 = Stub(SwiftBinary)
        def binary2 = Stub(SwiftBinary)

        when:
        def p = container.get(spec)
        container.add(binary1)
        container.add(binary2)

        then:
        0 * spec._

        when:
        container.realizeNow()

        then:
        1 * spec.isSatisfiedBy(binary1) >> true
        1 * spec.isSatisfiedBy(binary2) >> false
        0 * spec._

        when:
        p.get()
        p.get()
        p.get()

        then:
        0 * spec._
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
        e.message == 'Cannot query the value of this provider because it has no value available.'
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
        e.message == 'Cannot query the value of this provider because it has no value available.'
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

    def "when configuring by spec the spec is applied prior to configuring binary"() {
        def binary1 = Stub(SwiftBinary)
        def binary2 = Stub(SwiftBinary)
        def action1 = Mock(Action)
        def action2 = Mock(Action)
        def spec = Mock(Spec)

        when:
        def p = container.get(spec)
        p.configure(action1)
        container.configureEach(action2)

        container.add(binary1)
        container.add(binary2)

        then:
        0 * _

        when:
        container.realizeNow()

        then:
        1 * spec.isSatisfiedBy(binary1) >> true
        1 * spec.isSatisfiedBy(binary2) >> false

        then:
        1 * action1.execute(binary1)

        then:
        1 * action2.execute(binary1)
        1 * action2.execute(binary2)
        0 * _
    }

    def "cannot add configure by spec action when container is realizing"() {
        def spec = Stub(Spec)
        spec.isSatisfiedBy(_) >> true

        given:
        container.add(Stub(SwiftBinary))
        def p = container.get(spec)
        container.configureEach { p.configure { } }

        when:
        container.realizeNow()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot add actions to this collection as it has already been realized.'
    }

    def "cannot add configure by spec action after container is realized"() {
        def spec = Stub(Spec)
        spec.isSatisfiedBy(_) >> true

        given:
        container.add(Stub(SwiftBinary))
        def p = container.get(spec)
        container.realizeNow()

        when:
        p.configure { }

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot add actions to this collection as it has already been realized.'
    }

    def "can get by type and spec when element is already present"() {
        def spec = Stub(Spec)
        def binary1 = Stub(SwiftBinary)
        def binary2 = Stub(SwiftSharedLibrary)
        def binary3 = Stub(SwiftSharedLibrary)

        spec.isSatisfiedBy(binary2) >> true

        container.add(binary1)
        container.add(binary2)
        container.add(binary3)

        expect:
        def p = container.get(SwiftSharedLibrary, spec)
        !p.present

        container.realizeNow()

        p.present
        p.get() == binary2
    }

}
