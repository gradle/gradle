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

import org.gradle.internal.reflect.ObjectInstantiationException
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.UnknownServiceException
import spock.lang.Specification

import javax.inject.Inject

class DependencyInjectingInstantiatorTest extends Specification {
    final ServiceRegistry services = Mock()
    final DependencyInjectingInstantiator instantiator = new DependencyInjectingInstantiator(services, new DependencyInjectingInstantiator.ConstructorCache())

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
        _ * services.get(String) >> "string"

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

    def "constructors do not need to be public"() {
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

    def "propagates constructor failure"() {
        when:
        instantiator.newInstance(HasBrokenConstructor)

        then:
        ObjectInstantiationException e = thrown()
        e.cause == HasBrokenConstructor.failure
    }

    def "fails when too many constructor parameters provided"() {
        when:
        instantiator.newInstance(HasOneInjectConstructor, 12, "param2")

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "Too many parameters provided for constructor for class $HasOneInjectConstructor.name. Expected 1, received 2."
    }

    def "fails when supplied parameters cannot be used to call constructor"() {
        given:
        _ * services.get(Number) >> 12

        when:
        instantiator.newInstance(HasOneInjectConstructor, new StringBuilder("string"))

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "Unexpected parameter provided for constructor for class $HasOneInjectConstructor.name."
    }

    def "handles missing service"() {
        given:
        def failure = new UnknownServiceException(String, "unknown")
        _ * services.get(String) >> { throw failure }

        when:
        instantiator.newInstance(HasInjectConstructor, 12)

        then:
        ObjectInstantiationException e = thrown()
        e.cause == failure
    }

    def "fails when class has multiple constructors and none are annotated"() {
        when:
        instantiator.newInstance(HasNoInjectConstructor, new StringBuilder("param"))

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "Class $HasNoInjectConstructor.name has no constructor that is annotated with @Inject."
    }

    def "fails when class has multiple constructor that are annotated"() {
        when:
        instantiator.newInstance(HasMultipleInjectConstructors, new StringBuilder("param"))

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "Class $HasMultipleInjectConstructors.name has multiple constructors that are annotated with @Inject."
    }

    def "fails when class has non-public zero args constructor that is not annotated"() {
        when:
        instantiator.newInstance(HasNonPublicNoArgsConstructor, new StringBuilder("param"))

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "Class $HasNonPublicNoArgsConstructor.name has no constructor that is annotated with @Inject."
    }

    public static class HasDefaultConstructor {
    }

    public static class HasNonPublicNoArgsConstructor {
        protected HasNonPublicNoArgsConstructor() {
        }
    }

    public static class HasBrokenConstructor {
        static def failure = new RuntimeException()

        HasBrokenConstructor() {
            throw failure
        }
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

    public static class HasPrivateConstructor {
        @Inject
        private HasPrivateConstructor(String param1) {
        }
    }

    public static class HasMultipleInjectConstructors {
        @Inject HasMultipleInjectConstructors(String param1) {
        }

        @Inject HasMultipleInjectConstructors(Number param1) {
            throw new AssertionError()
        }

        @Inject HasMultipleInjectConstructors() {
            throw new AssertionError()
        }
    }

}
