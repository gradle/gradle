/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.internal.reflect

import org.gradle.api.reflect.ObjectInstantiationException
import spock.lang.Specification

class DirectInstantiatorTest extends Specification {
    final DirectInstantiator instantiator = DirectInstantiator.INSTANCE

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

    def "unboxes constructor parameters"() {
        expect:
        def result = instantiator.newInstance(SomeTypeWithPrimitiveTypes, true)
        result instanceof SomeTypeWithPrimitiveTypes
        result.result
    }

    def "fails when target class has ambiguous constructor"() {
        when:
        instantiator.newInstance(TypeWithAmbiguousConstructor, "param")

        then:
        ObjectInstantiationException e = thrown()
        e.cause instanceof IllegalArgumentException
        e.cause.message == "Found multiple public constructors for ${TypeWithAmbiguousConstructor} which accept parameters [java.lang.String]."

        when:
        instantiator.newInstance(TypeWithAmbiguousConstructor, true)

        then:
        e = thrown()
        e.cause.message == "Found multiple public constructors for ${TypeWithAmbiguousConstructor} which accept parameters [java.lang.Boolean]."
    }

    def "fails when target class has no matching public constructor"() {
        def param = new Object()

        when:
        instantiator.newInstance(SomeType, param)

        then:
        ObjectInstantiationException e = thrown()
        e.cause instanceof IllegalArgumentException
        e.cause.message == "Could not find any public constructor for ${SomeType} which accepts parameters [java.lang.Object]."

        when:
        instantiator.newInstance(SomeType, "a", "b")

        then:
        e = thrown()
        e.cause.message == "Could not find any public constructor for ${SomeType} which accepts parameters [java.lang.String, java.lang.String]."

        when:
        instantiator.newInstance(SomeType, false)

        then:
        e = thrown()
        e.cause.message == "Could not find any public constructor for ${SomeType} which accepts parameters [java.lang.Boolean]."

        when:

        instantiator.newInstance(SomeTypeWithPrimitiveTypes, "a")

        then:
        e = thrown()
        e.cause.message == "Could not find any public constructor for ${SomeTypeWithPrimitiveTypes} which accepts parameters [java.lang.String]."

        when:

        instantiator.newInstance(SomeTypeWithPrimitiveTypes, 22)

        then:
        e = thrown()
        e.cause.message == "Could not find any public constructor for ${SomeTypeWithPrimitiveTypes} which accepts parameters [java.lang.Integer]."

        when:

        instantiator.newInstance(SomeTypeWithPrimitiveTypes, [null] as Object[])

        then:
        e = thrown()
        e.cause.message == "Could not find any public constructor for ${SomeTypeWithPrimitiveTypes} which accepts parameters [null]."
    }

    def "wraps exception thrown by constructor"() {
        when:
        instantiator.newInstance(BrokenType, false)

        then:
        ObjectInstantiationException e = thrown()
        e.cause instanceof RuntimeException
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

class SomeTypeWithPrimitiveTypes {
    final Object result

    SomeTypeWithPrimitiveTypes(boolean result) {
        this.result = result
    }
}

class TypeWithAmbiguousConstructor {
    TypeWithAmbiguousConstructor(CharSequence param) {
    }

    TypeWithAmbiguousConstructor(String param) {
    }

    TypeWithAmbiguousConstructor(Boolean param) {
    }

    TypeWithAmbiguousConstructor(boolean param) {
    }
}
