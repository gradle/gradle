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

package org.gradle.internal

import spock.lang.Specification

class MutableReferenceTest extends Specification {

    def "initial value is kept"() {
        def object = "object"
        expect:
        MutableReference.empty().get() == null
        MutableReference.of(null).get() == null
        MutableReference.of(object).get() is object
    }

    def "can mutate value"() {
        def object = "object"
        def ref = MutableReference.empty()

        when:
        ref.set(object)
        then:
        ref.get() is object

        when:
        ref.set(null)
        then:
        ref.get() == null
    }

    def "equals works"() {
        def ref1 = MutableReference.of(new String("A"))
        def ref2 = MutableReference.of(new String("B"))
        def emptyRef = MutableReference.empty()

        expect:
        ref1 == ref1
        ref2 == ref2
        emptyRef == emptyRef
        ref1 != ref2
        emptyRef != ref1
        ref1 != emptyRef
    }

    def "hash code works"() {
        def values = [
            MutableReference.of(new String("A")),
            MutableReference.of(new String("A")),
            MutableReference.of(new String("B")),
            MutableReference.of(new String("B")),
            MutableReference.empty(),
            MutableReference.empty(),
        ] as Set

        expect:
        values == [
            MutableReference.of(new String("A")),
            MutableReference.of(new String("B")),
            MutableReference.empty(),
        ] as Set
    }
}
