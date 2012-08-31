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

import spock.lang.Specification
import org.gradle.internal.service.ServiceRegistry
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.internal.reflect.ObjectInstantiationException
import org.gradle.internal.service.UnknownServiceException

class DependencyInjectingInstantiatorTest extends Specification {
    final ServiceRegistry services = Mock()
    final Action warning = Mock()
    final DependencyInjectingInstantiator instantiator = new DependencyInjectingInstantiator(services, warning)

    def "creates instance that has default constructor"() {
        when:
        def result = instantiator.newInstance(HasDefaultConstructor)

        then:
        result != null
        0 * warning._
    }

    def "injects provided parameters into constructor"() {
        when:
        def result = instantiator.newInstance(HasInjectConstructor, "string", 12)

        then:
        result.param1 == "string"
        result.param2 == 12
        0 * warning._
    }

    def "injects missing parameters from provided service registry"() {
        given:
        _ * services.get(String) >> "string"

        when:
        def result = instantiator.newInstance(HasInjectConstructor, 12)

        then:
        result.param1 == "string"
        result.param2 == 12
        0 * warning._
    }

    def "unboxes primitive types"() {
        when:
        def result = instantiator.newInstance(AcceptsPrimitiveTypes, 12, true)

        then:
        result.param1 == 12
        result.param2
        0 * warning._
    }

    def "constructors do not need to be public"() {
        expect:
        instantiator.newInstance(HasPrivateConstructor, "param") != null
    }

    def "prefers exact match constructor when class has multiple constructors and none are annotated"() {
        when:
        def result = instantiator.newInstance(HasNoInjectConstructor, "param")

        then:
        result != null
        1 * warning.execute("Class $HasNoInjectConstructor.name has multiple constructors and no constructor is annotated with @Inject. In Gradle 2.0 this will be treated as an error.")
        0 * warning._
    }

    def "prefers exact match constructor when class has multiple constructors and another is annotated"() {
        when:
        def result = instantiator.newInstance(HasOneInjectConstructor, "param")

        then:
        result != null
        1 * warning.execute("Class $HasOneInjectConstructor.name has @Inject annotation on an unexpected constructor. In Gradle 2.0 the constructor annotated with @Inject will be used instead of the current default constructor.")
        0 * warning._
    }

    def "prefers exact match constructor when class has multiple annotated constructors"() {
        when:
        def result = instantiator.newInstance(HasMultipleInjectConstructors, "param")

        then:
        result != null
        1 * warning.execute("Class $HasMultipleInjectConstructors.name has multiple constructors with @Inject annotation. In Gradle 2.0 this will be treated as an error.")
        0 * warning._
    }

    def "warns when class has exactly one constructor that takes parameters parameters and is not annotated"() {
        when:
        def result = instantiator.newInstance(HasNonAnnotatedConstructor, "param")

        then:
        result != null
        1 * warning.execute("Constructor for class $HasNonAnnotatedConstructor.name is not annotated with @Inject. In Gradle 2.0 this will be treated as an error.")
        0 * warning._

        when:
        result = instantiator.newInstance(HasNonAnnotatedConstructor)

        then:
        result != null
        1 * warning.execute("Constructor for class $HasNonAnnotatedConstructor.name is not annotated with @Inject. In Gradle 2.0 this will be treated as an error.")
        0 * warning._
    }

    def "does not warn when class has multiple constructors and exact match constructor is annotated"() {
        when:
        def result = instantiator.newInstance(HasOneInjectConstructor, 12)

        then:
        result != null
        0 * warning._
    }

    def "does not warn when class has multiple constructors and there is no exact match and one constructor is annotated"() {
        given:
        _ * services.get(Number) >> 12

        when:
        def result = instantiator.newInstance(HasOneInjectConstructor)

        then:
        result != null
        0 * warning._
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
        instantiator.newInstance(HasNonAnnotatedConstructor)

        then:
        ObjectInstantiationException e = thrown()
        e.cause == failure
    }

    def "fails when class has multiple matching constructors"() {
        when:
        instantiator.newInstance(HasMultipleCompatibleConstructor, "param")

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "Class $HasMultipleCompatibleConstructor.name has multiple constructors that accept parameters [param]."
    }

    def "fails when class has no matching constructors and none are annotated"() {
        when:
        instantiator.newInstance(HasNoInjectConstructor, new StringBuilder("param"))

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "Class $HasNoInjectConstructor.name has no constructor that accepts parameters [param] or that is annotated with @Inject."
    }

    def "fails when class has no matching constructor and multiple are annotated"() {
        when:
        instantiator.newInstance(HasMultipleInjectConstructors, new StringBuilder("param"))

        then:
        ObjectInstantiationException e = thrown()
        e.cause.message == "Class $HasMultipleInjectConstructors.name has multiple constructors with @Inject annotation."
    }

    public static class HasDefaultConstructor {
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

    public static class HasNonAnnotatedConstructor {
        HasNonAnnotatedConstructor(String param1) {
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

    public static class HasMultipleCompatibleConstructor {
        HasMultipleCompatibleConstructor(String param1) {
        }

        HasMultipleCompatibleConstructor(Object param1) {
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
