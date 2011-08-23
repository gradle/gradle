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

import org.gradle.util.UncheckedException

class DirectInstantiatorTest extends Specification {
    final DirectInstantiator instantiator = new DirectInstantiator()

    def "creates instance with constructor parameters"() {
        CharSequence param = Mock()

        expect:
        def result = instantiator.newInstance(SomeType, param)
        result instanceof SomeType
        result.result == param
    }

    def "creates instance with subtypes of constructor parameters"() {
        expect:
        def result = instantiator.newInstance(SomeType, "param")
        result instanceof SomeType
        result.result == "param"
    }

    def "creates instance with null constructor parameters"() {
        expect:
        def result = instantiator.newInstance(SomeType, [null] as Object[])
        result instanceof SomeType
        result.result == null
    }

    def "uses constructor which matches parameter types"() {
        expect:
        def stringResult = instantiator.newInstance(SomeTypeWithMultipleConstructors, "param")
        stringResult instanceof SomeTypeWithMultipleConstructors
        stringResult.result == "param"

        and:
        def numberResult = instantiator.newInstance(SomeTypeWithMultipleConstructors, 5)
        numberResult instanceof SomeTypeWithMultipleConstructors
        numberResult.result == 5
    }

    def "fails when target class has ambiguous constructor"() {
        when:
        instantiator.newInstance(TypeWithAmbiguousConstructor, "param")

        then:
        IllegalArgumentException e = thrown()
        e.message == "Found multiple public constructors for ${TypeWithAmbiguousConstructor} which accept parameters [param]."

        when:
        instantiator.newInstance(TypeWithAmbiguousConstructor, [null] as Object[])

        then:
        e = thrown()
        e.message == "Found multiple public constructors for ${TypeWithAmbiguousConstructor} which accept parameters [null]."
    }

    def "fails when target class has no matching public constructor"() {
        def param = new Object()

        when:
        instantiator.newInstance(SomeType, param)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Could not find any public constructor for ${SomeType} which accepts parameters [${param}]."

        when:
        instantiator.newInstance(SomeType, "a", "b")

        then:
        e = thrown()
        e.message == "Could not find any public constructor for ${SomeType} which accepts parameters [a, b]."
    }

    def "rethrows unchecked exception thrown by constructor"() {
        when:
        instantiator.newInstance(BrokenType, false)

        then:
        RuntimeException e = thrown()
        e.message == 'broken'
    }

    def "wraps checked exception thrown by constructor"() {
        when:
        instantiator.newInstance(BrokenType, true)

        then:
        UncheckedException e = thrown()
        e.cause.message == 'broken'
    }
}

class SomeType {
    final Object result

    SomeType(CharSequence result) {
        this.result = result
    }
}

class BrokenType {
    BrokenType(Boolean checked) {
        throw checked ? new Exception("broken") : new RuntimeException("broken")
    }
}

class SomeTypeWithMultipleConstructors {
    final Object result

    SomeTypeWithMultipleConstructors(CharSequence result) {
        this.result = result
    }

    SomeTypeWithMultipleConstructors(Number result) {
        this.result = result
    }
}

class TypeWithAmbiguousConstructor {
    TypeWithAmbiguousConstructor(Object param) {
    }

    TypeWithAmbiguousConstructor(String param) {
    }
}