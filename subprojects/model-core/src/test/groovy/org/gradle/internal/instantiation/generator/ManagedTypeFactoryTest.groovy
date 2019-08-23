/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.instantiation.generator

import org.gradle.internal.instantiation.ManagedTypeFactory
import spock.lang.Specification

class ManagedTypeFactoryTest extends Specification {
    def "can generate a managed type instance from state"() {
        def factory = new ManagedTypeFactory(ManagedThing)

        expect:
        def newManagedThing = factory.fromState(ManagedThing, ["foo"] as Object[])
        newManagedThing.firstValue == "foo"
    }

    def "throws an exception when type does not look like a managed type"() {
        when:
        new ManagedTypeFactory(AnotherThing)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "The class AnotherThing does not appear to a be a generated managed class."
        e.cause instanceof NoSuchMethodException
    }

    static class ManagedThing {
        final String firstValue
        public ManagedThing(Object[] values) {
            this.firstValue = values[0]
        }
    }

    static class AnotherThing { }
}
