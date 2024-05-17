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

import org.gradle.api.reflect.ObjectInstantiationException
import org.gradle.internal.service.ServiceLookup
import org.gradle.util.internal.TextUtil
import spock.lang.Specification

class DependencyInjectionUsingLenientConstructorSelectorTest extends Specification {
    def services = Mock(ServiceLookup)
    def classGenerator = new IdentityClassGenerator()
    def instantiator = new DependencyInjectingInstantiator(new ParamsMatchingConstructorSelector(classGenerator), services)

    def "creates instance that has default constructor"() {
        when:
        def result = instantiator.newInstance(HasDefaultConstructor)

        then:
        result instanceof HasDefaultConstructor
    }

    def "injects provided parameters into constructor"() {
        when:
        def result = instantiator.newInstance(HasConstructor, "string", 12)

        then:
        result.param1 == "string"
        result.param2 == 12
    }

    def "injects missing parameters from provided service registry"() {
        given:
        services.find(String) >> "string"

        when:
        def result = instantiator.newInstance(HasConstructor, 12)

        then:
        result.param1 == "string"
        result.param2 == 12
    }

    def "unboxes primitive types"() {
        when:
        def result = instantiator.newInstance(AcceptsPrimitiveTypes, 12, true)

        then:
        result.param1 == 12
        result.param2
    }

    def "allows null parameters"() {
        when:
        def result = instantiator.newInstance(HasConstructor, null, null)

        then:
        result.param1 == null
        result.param2 == null
    }

    def "allows null parameters when services expected"() {
        given:
        services.find(Number) >> 12

        when:
        def result = instantiator.newInstance(HasConstructor, [null] as Object[])

        then:
        result.param1 == null
        result.param2 == 12
    }

    def "fails when null value is provided for primitive parameter"() {
        when:
        instantiator.newInstance(AcceptsPrimitiveTypes, 12, null)

        then:
        ObjectInstantiationException e = thrown()
        e.cause instanceof IllegalArgumentException
        e.cause.message == "Unable to determine constructor argument #2: null value is not assignable to type boolean."
    }

    def "fails when null value is provided for primitive parameter and services expected"() {
        when:
        instantiator.newInstance(AcceptsPrimitiveTypes, [null] as Object[])

        then:
        ObjectInstantiationException e = thrown()
        e.cause instanceof IllegalArgumentException
        e.cause.message == "Unable to determine constructor argument #1: null value is not assignable to type int."
    }

    def "fails when parameters do not match constructor"() {
        when:
        instantiator.newInstance(HasConstructor, ["a", "b"] as Object[])

        then:
        ObjectInstantiationException e = thrown()
        e.cause instanceof IllegalArgumentException
        e.cause.message == "Unable to determine constructor argument #2: value 'b' not assignable to type Number."
    }

    def "fails when no constructors match parameters"() {
        when:
        instantiator.newInstance(HasConstructors, ["a", "b"] as Object[])

        then:
        ObjectInstantiationException e = thrown()
        e.cause instanceof IllegalArgumentException
        e.cause.message == "No constructors of type DependencyInjectionUsingLenientConstructorSelectorTest.HasConstructors match parameters: ['a', 'b']"
    }

    def "fails when constructors are ambiguous"() {
        when:
        instantiator.newInstance(HasConstructors, ["a"] as Object[])

        then:
        ObjectInstantiationException e = thrown()
        e.cause instanceof IllegalArgumentException
        TextUtil.normaliseLineSeparators(e.cause.message) == """Multiple constructors for parameters ['a']:
  1. candidate: DependencyInjectionUsingLenientConstructorSelectorTest.HasConstructors(String, Number)
  2. best match: DependencyInjectionUsingLenientConstructorSelectorTest.HasConstructors(String, boolean)"""
    }

    def "uses exact match constructor when constructors could be ambiguous with multiple parameters"() {
        when:
        // HasSeveralParameters has 3 constructors
        // 1 with 3 parameters and 2 with 4 parameters
        // the constructor with 3 parameters is a subset of the 4 parameter constructor
        def inst = instantiator.newInstance(HasSeveralParameters, ["a", 0, false] as Object[])

        then:
        inst.assertConstructor("String a, int b, boolean d")
    }

    def "fails on non-static inner class when outer type not provided as first parameter"() {
        when:
        def inst = instantiator.newInstance(NonStatic, this)

        then:
        inst.owner == this

        when:
        instantiator.newInstance(NonStatic)

        then:
        ObjectInstantiationException e = thrown()
        e.cause instanceof IllegalArgumentException
        e.cause.message == "Class DependencyInjectionUsingLenientConstructorSelectorTest.NonStatic is a non-static inner class."
    }

    def "fails on non-static inner class when outer type not provided as first parameter when type takes constructor params"() {
        given:
        services.find(Number) >> 12

        when:
        def inst = instantiator.newInstance(NonStaticWithParams, this, "param")

        then:
        inst.owner == this

        when:
        instantiator.newInstance(NonStaticWithParams, "param")

        then:
        ObjectInstantiationException e = thrown()
        e.cause instanceof IllegalArgumentException
        e.cause.message == "Class DependencyInjectionUsingLenientConstructorSelectorTest.NonStaticWithParams is a non-static inner class."
    }

    static class HasDefaultConstructor {
    }

    static class HasConstructor {
        String param1
        Number param2

        HasConstructor(String param1, Number param2) {
            this.param1 = param1
            this.param2 = param2
        }
    }

    static class HasSeveralParameters {
        private final String usedConstructor

        HasSeveralParameters(String a, int b, String x, boolean d) {
            this("String a, int b, String x, boolean d")
        }
        HasSeveralParameters(String a, int b, int c, boolean d) {
            this("String a, int b, int c, boolean d")
        }
        HasSeveralParameters(String a, int b, boolean d) {
            this("String a, int b, boolean d")
        }

        private HasSeveralParameters(String usedConstructor) {
            this.usedConstructor = usedConstructor
        }

        void assertConstructor(String expectedConstructor) {
            assert usedConstructor == expectedConstructor
        }
    }
    static class HasConstructors {
        HasConstructors(String param1, Number param2) {
        }
        HasConstructors(String param1, boolean param2) {
        }
    }

    static class AcceptsPrimitiveTypes {
        int param1
        boolean param2

        AcceptsPrimitiveTypes(int param1, boolean param2) {
            this.param1 = param1
            this.param2 = param2
        }
    }

    class NonStatic {
        Object getOwner() {
            return DependencyInjectionUsingLenientConstructorSelectorTest.this
        }
    }

    class NonStaticWithParams {
        NonStaticWithParams(String p, Number n) {
        }

        Object getOwner() {
            return DependencyInjectionUsingLenientConstructorSelectorTest.this
        }
    }
}
