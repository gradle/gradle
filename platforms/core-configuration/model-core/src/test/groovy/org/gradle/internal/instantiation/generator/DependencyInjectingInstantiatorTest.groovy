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
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.service.ServiceLookup
import spock.lang.Specification

import javax.inject.Inject

class DependencyInjectingInstantiatorTest extends Specification {
    def services = Mock(ServiceLookup)
    def classGenerator = new IdentityClassGenerator()
    def instantiator = new DependencyInjectingInstantiator(new Jsr330ConstructorSelector(classGenerator, new TestCrossBuildInMemoryCacheFactory.TestCache()), services)

    def "creates instance that has default constructor"() {
        when:
        def result = instantiator.newInstance(HasDefaultConstructor)

        then:
        result instanceof HasDefaultConstructor
    }

    def "injects provided parameters into constructor"() {
        when:
        def result = instantiator.newInstance(HasInjectConstructor, "string", 12)

        then:
        result.param1 == "string"
        result.param2 == 12
    }

    def "injects missing parameters from provided service registry"() {
        given:
        services.find(String) >> "string"

        when:
        def result = instantiator.newInstance(HasInjectConstructor, 12)

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

    def "constructors do not need to be public but do need to be annotated"() {
        expect:
        instantiator.newInstance(HasPrivateConstructor, "param") != null
    }

    def "class can be package scoped"() {
        expect:
        instantiator.newInstance(PackageScopedClass) != null
    }

    def "selects annotated constructor when class has multiple constructors and only one is annotated"() {
        when:
        def result = instantiator.newInstance(HasOneInjectConstructor, 12)

        then:
        result != null
    }

    def "class can have private constructor with args and annotated"() {
        when:
        def result = instantiator.newInstance(HasPrivateArgsInjectConstructor, "param")

        then:
        result != null
    }

    def "class can be private and have public constructor"() {
        when:
        def result = instantiator.newInstance(PrivateWithValidConstructor, "param")

        then:
        result != null
    }

    def "wraps constructor failure"() {
        when:
        instantiator.newInstance(HasBrokenConstructor)

        then:
        ObjectInstantiationException e = thrown()
        e.cause == HasBrokenConstructor.failure
    }

    def "reports requested type rather than implementation type on constructor failure"() {
        given:
        classGenerator.generate(HasBrokenConstructor) >> HasBrokenConstructorSub

        when:
        instantiator.newInstance(HasBrokenConstructor)

        then:
        ObjectInstantiationException e = thrown()
        e.message == "Could not create an instance of type $HasBrokenConstructor.name."
        e.cause == HasBrokenConstructor.failure
    }

    def "fails when too many constructor parameters provided"() {
        when:
        instantiator.newInstance(HasOneInjectConstructor, 12, "param2")

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "Too many parameters provided for constructor for type DependencyInjectingInstantiatorTest.HasOneInjectConstructor. Expected 1, received 2."
    }

    def "fails when supplied parameters cannot be used to call constructor"() {
        given:
        services.find(Number) >> 12

        when:
        instantiator.newInstance(HasOneInjectConstructor, new StringBuilder("string"))

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "Unable to determine constructor argument #1: value string not assignable to type Number."
    }

    def "fails on missing service"() {
        given:
        services.find(String) >> null

        when:
        instantiator.newInstance(HasInjectConstructor, 12)

        then:
        ObjectInstantiationException e = thrown()
        e.cause instanceof IllegalArgumentException
        e.cause.message == "Unable to determine constructor argument #1: value 12 is not assignable to type String, or no service of type String."
    }

    def "fails on non-static inner class"() {
        when:
        instantiator.newInstance(NonStatic, "param")

        then:
        ObjectInstantiationException e = thrown()
        e.cause instanceof IllegalArgumentException
        e.cause.message == "Class DependencyInjectingInstantiatorTest.NonStatic is a non-static inner class."
    }

    def "fails when class has multiple constructors and none are annotated"() {
        when:
        instantiator.newInstance(HasNoInjectConstructor, new StringBuilder("param"))

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "Class DependencyInjectingInstantiatorTest.HasNoInjectConstructor has no constructor that is annotated with @Inject."
    }

    def "fails when class has multiple constructors with different visibilities and none are annotated"() {
        when:
        instantiator.newInstance(HasMixedConstructors, new StringBuilder("param"))

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "Class DependencyInjectingInstantiatorTest.HasMixedConstructors has no constructor that is annotated with @Inject."
    }

    def "fails when class has multiple constructors that are annotated"() {
        when:
        instantiator.newInstance(HasMultipleInjectConstructors, new StringBuilder("param"))

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "Class DependencyInjectingInstantiatorTest.HasMultipleInjectConstructors has multiple constructors that are annotated with @Inject."
    }

    def "fails when class has multiple constructors with different visibilities that are annotated"() {
        when:
        instantiator.newInstance(HasMixedInjectConstructors, new StringBuilder("param"))

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "Class DependencyInjectingInstantiatorTest.HasMixedInjectConstructors has multiple constructors that are annotated with @Inject."
    }

    def "fails when class has non-public zero args constructor that is not annotated"() {
        given:
        classGenerator.generate(HasNonPublicNoArgsConstructor) >> HasNonPublicNoArgsConstructorSub

        when:
        instantiator.newInstance(HasNonPublicNoArgsConstructor, new StringBuilder("param"))

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "The constructor for type DependencyInjectingInstantiatorTest.HasNonPublicNoArgsConstructor should be public or package protected or annotated with @Inject."
    }

    def "fails when class has public constructor with args and that is not annotated"() {
        given:
        classGenerator.generate(HasSingleConstructorWithArgsAndNoAnnotation) >> HasSingleConstructorWithArgsAndNoAnnotationSub

        when:
        instantiator.newInstance(HasSingleConstructorWithArgsAndNoAnnotation, "param")

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "The constructor for type DependencyInjectingInstantiatorTest.HasSingleConstructorWithArgsAndNoAnnotation should be annotated with @Inject."
    }

    def "fails when class has private constructor with args and that is not annotated"() {
        when:
        instantiator.newInstance(HasPrivateArgsConstructor, new StringBuilder("param"))

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "The constructor for type DependencyInjectingInstantiatorTest.HasPrivateArgsConstructor should be annotated with @Inject."
    }

    def "fails when null passed as constructor argument value"() {
        when:
        instantiator.newInstance(HasInjectConstructor, null, 12)

        then:
        ObjectInstantiationException e = thrown()
        e.cause instanceof IllegalArgumentException
        e.cause.message == "Null value provided in parameters [null, 12]"
    }

    def "fails when null passed as constructor argument value and services expected"() {
        when:
        instantiator.newInstance(HasInjectConstructor, [null] as Object[])

        then:
        ObjectInstantiationException e = thrown()
        e.cause instanceof IllegalArgumentException
        e.cause.message == "Null value provided in parameters [null]"
    }

    def "selects @Inject constructor over no-args constructor"() {
        when:
        def result = instantiator.newInstance(HasDefaultAndInjectConstructors, "ignored")

        then:
        result.message == "injected"
    }

    static class HasDefaultConstructor {
    }

    static class HasNonPublicNoArgsConstructor {
        protected HasNonPublicNoArgsConstructor() {
        }
    }

    static class HasPrivateArgsInjectConstructor {
        @Inject
        private HasPrivateArgsInjectConstructor(String param) {
        }
    }

    static class HasPrivateArgsConstructor {
        private HasPrivateArgsConstructor(String param) {
        }
    }

    private static class PrivateWithValidConstructor {
        @Inject
        public PrivateWithValidConstructor(String param) {
        }
    }

    static class HasNonPublicNoArgsConstructorSub extends HasNonPublicNoArgsConstructor {
        protected HasNonPublicNoArgsConstructorSub() {
        }
    }

    static class HasSingleConstructorWithArgsAndNoAnnotation {
        HasSingleConstructorWithArgsAndNoAnnotation(String arg) {
        }
    }

    static class HasSingleConstructorWithArgsAndNoAnnotationSub extends HasSingleConstructorWithArgsAndNoAnnotation {
        HasSingleConstructorWithArgsAndNoAnnotationSub(String arg) {
            super(arg)
        }
    }

    static class HasBrokenConstructor {
        static def failure = new RuntimeException()

        HasBrokenConstructor() {
            throw failure
        }
    }

    static class HasBrokenConstructorSub extends HasBrokenConstructor {
    }

    static class HasInjectConstructor {
        String param1
        Number param2

        @Inject
        HasInjectConstructor(String param1, Number param2) {
            this.param1 = param1
            this.param2 = param2
        }
    }

    static class AcceptsPrimitiveTypes {
        int param1
        boolean param2

        @Inject
        AcceptsPrimitiveTypes(int param1, boolean param2) {
            this.param1 = param1
            this.param2 = param2
        }
    }

    static class HasOneInjectConstructor {
        HasOneInjectConstructor(String param1) {
        }

        @Inject
        HasOneInjectConstructor(Number param1) {
        }
    }

    static class HasNoInjectConstructor {
        HasNoInjectConstructor(String param1) {
        }

        HasNoInjectConstructor(Number param1) {
            throw new AssertionError()
        }

        HasNoInjectConstructor() {
            throw new AssertionError()
        }
    }

    static class HasMixedConstructors {
        HasMixedConstructors(String param1) {
        }

        private HasMixedConstructors(Number param1) {
            throw new AssertionError()
        }

        private HasMixedConstructors() {
            throw new AssertionError()
        }
    }

    static class HasPrivateConstructor {
        @Inject
        private HasPrivateConstructor(String param1) {
        }
    }

    static class HasMultipleInjectConstructors {
        @Inject
        HasMultipleInjectConstructors(String param1) {
        }

        @Inject
        HasMultipleInjectConstructors(Number param1) {
            throw new AssertionError()
        }

        @Inject
        HasMultipleInjectConstructors() {
            throw new AssertionError()
        }
    }

    static class HasMixedInjectConstructors {
        @Inject
        public HasMixedInjectConstructors(String param1) {
        }

        @Inject
        private HasMixedInjectConstructors(Number param1) {
            throw new AssertionError()
        }

        @Inject
        private HasMixedInjectConstructors() {
            throw new AssertionError()
        }
    }

    static class HasDefaultAndInjectConstructors {
        final String message

        @Inject
        public HasDefaultAndInjectConstructors(String ignored) {
            message = "injected"
        }

        public HasDefaultAndInjectConstructors() {
            message = "default"
        }
    }

    class NonStatic {
    }
}
