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

package org.gradle.api.internal

import org.gradle.api.internal.instantiation.Jsr330ConstructorSelector
import org.gradle.api.reflect.ObjectInstantiationException
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.service.ServiceRegistry
import spock.lang.Specification

import javax.inject.Inject

class DependencyInjectingInstantiatorTest extends Specification {
    def services = Mock(ServiceRegistry)
    def classGenerator = Mock(ClassGenerator)
    def instantiator = new DependencyInjectingInstantiator(new Jsr330ConstructorSelector(classGenerator, new TestCrossBuildInMemoryCacheFactory.TestCache<Class<?>, DependencyInjectingInstantiator.CachedConstructor>()), services)

    def "creates instance that has default constructor"() {
        given:
        classGenerator.generate(_) >> { Class<?> c -> c }

        when:
        def result = instantiator.newInstance(HasDefaultConstructor)

        then:
        result instanceof HasDefaultConstructor
    }

    def "injects provided parameters into constructor"() {
        given:
        classGenerator.generate(_) >> { Class<?> c -> c }

        when:
        def result = instantiator.newInstance(HasInjectConstructor, "string", 12)

        then:
        result.param1 == "string"
        result.param2 == 12
    }

    def "injects missing parameters from provided service registry"() {
        given:
        classGenerator.generate(_) >> { Class<?> c -> c }
        services.find(String) >> "string"

        when:
        def result = instantiator.newInstance(HasInjectConstructor, 12)

        then:
        result.param1 == "string"
        result.param2 == 12
    }

    def "unboxes primitive types"() {
        given:
        classGenerator.generate(_) >> { Class<?> c -> c }

        when:
        def result = instantiator.newInstance(AcceptsPrimitiveTypes, 12, true)

        then:
        result.param1 == 12
        result.param2
    }

    def "constructors do not need to be public but do need to be annotated"() {
        given:
        classGenerator.generate(_) >> { Class<?> c -> c }

        expect:
        instantiator.newInstance(HasPrivateConstructor, "param") != null
    }

    def "class can be package scoped"() {
        given:
        classGenerator.generate(_) >> { Class<?> c -> c }

        expect:
        instantiator.newInstance(PackageScopedClass) != null
    }

    def "selects annotated constructor when class has multiple constructors and only one is annotated"() {
        given:
        classGenerator.generate(_) >> { Class<?> c -> c }

        when:
        def result = instantiator.newInstance(HasOneInjectConstructor, 12)

        then:
        result != null
    }

    def "class can have private constructor with args and annotated"() {
        given:
        classGenerator.generate(_) >> { Class<?> c -> c }

        when:
        def result = instantiator.newInstance(HasPrivateArgsInjectConstructor, "param")

        then:
        result != null
    }

    def "class can be private and have public constructor"() {
        given:
        classGenerator.generate(_) >> { Class<?> c -> c }

        when:
        def result = instantiator.newInstance(PrivateWithValidConstructor, "param")

        then:
        result != null
    }

    def "wraps constructor failure"() {
        given:
        classGenerator.generate(_) >> { Class<?> c -> c }

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
        given:
        classGenerator.generate(_) >> { Class<?> c -> c }

        when:
        instantiator.newInstance(HasOneInjectConstructor, 12, "param2")

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "Too many parameters provided for constructor for class $HasOneInjectConstructor.name. Expected 1, received 2."
    }

    def "fails when supplied parameters cannot be used to call constructor"() {
        given:
        classGenerator.generate(_) >> { Class<?> c -> c }
        services.find(Number) >> 12

        when:
        instantiator.newInstance(HasOneInjectConstructor, new StringBuilder("string"))

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "Unexpected parameter provided for constructor for class $HasOneInjectConstructor.name."
    }

    def "fails on missing service"() {
        given:
        classGenerator.generate(_) >> { Class<?> c -> c }
        services.find(String) >> null

        when:
        instantiator.newInstance(HasInjectConstructor, 12)

        then:
        ObjectInstantiationException e = thrown()
        e.cause instanceof IllegalArgumentException
        e.cause.message == "Unable to determine $HasInjectConstructor.name argument #1: value 12 not assignable to type class java.lang.String, or no service of type class java.lang.String"
    }

    def "fails when class has multiple constructors and none are annotated"() {
        given:
        classGenerator.generate(_) >> { Class<?> c -> c }

        when:
        instantiator.newInstance(HasNoInjectConstructor, new StringBuilder("param"))

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "Class $HasNoInjectConstructor.name has no constructor that is annotated with @Inject."
    }

    def "fails when class has multiple constructors with different visibilities and none are annotated"() {
        given:
        classGenerator.generate(_) >> { Class<?> c -> c }

        when:
        instantiator.newInstance(HasMixedConstructors, new StringBuilder("param"))

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "Class $HasMixedConstructors.name has no constructor that is annotated with @Inject."
    }

    def "fails when class has multiple constructors that are annotated"() {
        given:
        classGenerator.generate(_) >> { Class<?> c -> c }

        when:
        instantiator.newInstance(HasMultipleInjectConstructors, new StringBuilder("param"))

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "Class $HasMultipleInjectConstructors.name has multiple constructors that are annotated with @Inject."
    }

    def "fails when class has multiple constructors with different visibilities that are annotated"() {
        given:
        classGenerator.generate(_) >> { Class<?> c -> c }

        when:
        instantiator.newInstance(HasMixedInjectConstructors, new StringBuilder("param"))

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "Class $HasMixedInjectConstructors.name has multiple constructors that are annotated with @Inject."
    }

    def "fails when class has non-public zero args constructor that is not annotated"() {
        given:
        classGenerator.generate(HasNonPublicNoArgsConstructor) >> HasNonPublicNoArgsConstructorSub

        when:
        instantiator.newInstance(HasNonPublicNoArgsConstructor, new StringBuilder("param"))

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "The constructor for class $HasNonPublicNoArgsConstructor.name should be public or package protected or annotated with @Inject."
    }

    def "fails when class has public constructor with args and that is not annotated"() {
        given:
        classGenerator.generate(HasSingleConstructorWithArgsAndNoAnnotation) >> HasSingleConstructorWithArgsAndNoAnnotationSub

        when:
        instantiator.newInstance(HasSingleConstructorWithArgsAndNoAnnotation, "param")

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "The constructor for class $HasSingleConstructorWithArgsAndNoAnnotation.name should be annotated with @Inject."
    }

    def "fails when class has private constructor with args and that is not annotated"() {
        given:
        classGenerator.generate(_) >> { Class<?> c -> c }

        when:
        instantiator.newInstance(HasPrivateArgsConstructor, new StringBuilder("param"))

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "The constructor for class $HasPrivateArgsConstructor.name should be annotated with @Inject."
    }

    def "fails when null passed as constructor argument value"() {
        given:
        classGenerator.generate(_) >> { Class<?> c -> c }
        services.find(String) >> null

        when:
        instantiator.newInstance(HasInjectConstructor, null, null)

        then:
        ObjectInstantiationException e = thrown()
        e.cause instanceof IllegalArgumentException
        e.cause.message == "Unable to determine $HasInjectConstructor.name argument #1: value null not assignable to type class java.lang.String, or no service of type class java.lang.String"
    }

    def "selects @Inject constructor over no-args constructor"() {
        given:
        classGenerator.generate(_) >> { Class<?> c -> c }

        when:
        def result = instantiator.newInstance(HasDefaultAndInjectConstructors, "ignored")

        then:
        result.message == "injected"
    }

    public static class HasDefaultConstructor {
    }

    public static class HasNonPublicNoArgsConstructor {
        protected HasNonPublicNoArgsConstructor() {
        }
    }

    public static class HasPrivateArgsInjectConstructor {
        @Inject
        private HasPrivateArgsInjectConstructor(String param) {
        }
    }

    public static class HasPrivateArgsConstructor {
        private HasPrivateArgsConstructor(String param) {
        }
    }

    private static class PrivateWithValidConstructor {
        @Inject
        public PrivateWithValidConstructor(String param) {
        }
    }

    public static class HasNonPublicNoArgsConstructorSub extends HasNonPublicNoArgsConstructor {
        protected HasNonPublicNoArgsConstructorSub() {
        }
    }

    public static class HasSingleConstructorWithArgsAndNoAnnotation {
        HasSingleConstructorWithArgsAndNoAnnotation(String arg) {
        }
    }

    public static class HasSingleConstructorWithArgsAndNoAnnotationSub extends HasSingleConstructorWithArgsAndNoAnnotation {
        HasSingleConstructorWithArgsAndNoAnnotationSub(String arg) {
            super(arg)
        }
    }

    public static class HasBrokenConstructor {
        static def failure = new RuntimeException()

        HasBrokenConstructor() {
            throw failure
        }
    }

    public static class HasBrokenConstructorSub extends HasBrokenConstructor {
    }

    public static class HasInjectConstructor {
        String param1
        Number param2

        @Inject
        HasInjectConstructor(String param1, Number param2) {
            this.param1 = param1
            this.param2 = param2
        }
    }

    public static class AcceptsPrimitiveTypes {
        int param1
        boolean param2

        @Inject
        AcceptsPrimitiveTypes(int param1, boolean param2) {
            this.param1 = param1
            this.param2 = param2
        }
    }

    public static class HasOneInjectConstructor {
        HasOneInjectConstructor(String param1) {
        }

        @Inject
        HasOneInjectConstructor(Number param1) {
        }
    }

    public static class HasNoInjectConstructor {
        HasNoInjectConstructor(String param1) {
        }

        HasNoInjectConstructor(Number param1) {
            throw new AssertionError()
        }

        HasNoInjectConstructor() {
            throw new AssertionError()
        }
    }

    public static class HasMixedConstructors {
        HasMixedConstructors(String param1) {
        }

        private HasMixedConstructors(Number param1) {
            throw new AssertionError()
        }

        private HasMixedConstructors() {
            throw new AssertionError()
        }
    }

    public static class HasPrivateConstructor {
        @Inject
        private HasPrivateConstructor(String param1) {
        }
    }

    public static class HasMultipleInjectConstructors {
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

    public static class HasMixedInjectConstructors {
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

    public static class HasDefaultAndInjectConstructors {
        final String message

        @Inject
        public HasDefaultAndInjectConstructors(String ignored) {
            message = "injected"
        }

        public HasDefaultAndInjectConstructors() {
            message = "default"
        }
    }
}
