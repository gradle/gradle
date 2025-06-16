/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.instantiation.managed

import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistrationAction
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.ServiceRegistryBuilder
import spock.lang.Specification

/**
 * Tests {@link ManagedObjectRegistry}.
 */
class ManagedObjectRegistryTest extends Specification {

    static class Thing {}

    static class OneParamThing<T> {
        Class<T> one
        OneParamThing(Class<T> one) {
            this.one = one
        }
    }

    static class TwoParamThing<T, U> {
        Class<T> one
        Class<U> two

        TwoParamThing(Class<T> one, Class<U> two) {
            this.one = one
            this.two = two
        }
    }

    interface NonProvidingInterface {

        @ManagedObjectCreator
        Thing createThing()

    }

    static class NonProvidingImplementation implements NonProvidingInterface {

        @Override
        Thing createThing() {
            return new Thing()
        }

    }

    def "does not detect non-providing interfaces"() {
        def registry = registryOf {
            it.add(NonProvidingInterface, NonProvidingImplementation)
        }

        when:
        def instance = registry.newInstance(Thing)

        then:
        instance == null
    }

    static class NonProvidingImplementationWithCreator implements NonProvidingInterface {

        @Override
        @ManagedObjectCreator
        Thing createThing() {
            return new Thing()
        }

    }

    def "does not detect non-providing implementations"() {
        def registry = registryOf {
            it.add(NonProvidingImplementationWithCreator)
        }

        when:
        def instance = registry.newInstance(Thing)

        then:
        instance == null
    }

    @ManagedObjectProvider
    interface ProvidingInterface {

        @ManagedObjectCreator
        Thing createThing()

    }

    static class ProvidingImplementation implements ProvidingInterface {

        @Override
        Thing createThing() {
            return new Thing()
        }

    }

    def "can detect providing interfaces"() {
        def registry = registryOf {
            it.add(ProvidingInterface, ProvidingImplementation)
        }

        when:
        def instance = registry.newInstance(Thing)

        then:
        instance instanceof Thing
    }

    @ManagedObjectProvider
    static class ProvidingImplementationWithCreator {

        @ManagedObjectCreator
        Thing createThing() {
            return new Thing()
        }

    }

    def "can detect providing implementations"() {
        def registry = registryOf {
            it.add(ProvidingImplementationWithCreator)
        }

        when:
        def instance = registry.newInstance(Thing)

        then:
        instance instanceof Thing
    }


    @ManagedObjectProvider
    interface ProvidingInterfaceWithoutCreator {

        Thing createThing()

    }

    static class ProvidingInterfaceWithoutCreatorWithCreator implements ProvidingInterfaceWithoutCreator {

        @Override
        @ManagedObjectCreator
        Thing createThing() {
            return new Thing()
        }

    }

    def "creator annotation may be placed on implementation with provider on interface"() {
        def registry = registryOf {
            it.add(ProvidingInterfaceWithoutCreator, ProvidingInterfaceWithoutCreatorWithCreator)
        }

        when:
        def instance = registry.newInstance(Thing)

        then:
        instance instanceof Thing
    }

    @ManagedObjectProvider
    static class ManyProvidingImplementation {

        @ManagedObjectCreator
        Thing createThing() {
            return new Thing()
        }

        @ManagedObjectCreator
        <T> OneParamThing<T> createOneParamThing(Class<T> one) {
            return new OneParamThing<>(one)
        }

        @ManagedObjectCreator
        <T, U> TwoParamThing<T, U> createTwoParamThing(Class<T> one, Class<U> two) {
            return new TwoParamThing<>(one, two)
        }
    }

    def "can create types with one parameter"() {
        def registry = registryOf {
            it.add(ManyProvidingImplementation)
        }

        when:
        registry.newInstance(OneParamThing)

        then:
        def e = thrown(RuntimeException)
        e.message == "Could not create managed object."

        when:
        OneParamThing<String> oneParamInstance = registry.newInstance(OneParamThing, String)

        then:
        oneParamInstance instanceof OneParamThing
        oneParamInstance.one == String

        when:
        registry.newInstance(OneParamThing, String, Object)

        then:
        e = thrown(RuntimeException)
        e.message == "Could not create managed object."
    }

    def "can create types with two parameters"() {
        def registry = registryOf {
            it.add(ManyProvidingImplementation)
        }

        when:
        registry.newInstance(TwoParamThing)

        then:
        def e = thrown(RuntimeException)
        e.message == "Could not create managed object."

        when:
        registry.newInstance(TwoParamThing, String)

        then:
        e = thrown(RuntimeException)
        e.message == "Could not create managed object."

        when:
        TwoParamThing<String, Integer> twoParamInstance = registry.newInstance(TwoParamThing, String, Integer)

        then:
        twoParamInstance instanceof TwoParamThing
        twoParamInstance.one == String
        twoParamInstance.two == Integer
    }

    def "child registries delegate to parent registries"() {
        def parentRegistry = registryOf {
            it.add(ManyProvidingImplementation)
        }
        def registry = new ManagedObjectRegistry(parentRegistry)

        when:
        Thing thing = registry.newInstance(Thing)

        then:
        thing instanceof Thing

        when:
        OneParamThing<String> oneParamInstance = registry.newInstance(OneParamThing, String)

        then:
        oneParamInstance instanceof OneParamThing
        oneParamInstance.one == String

        when:
        TwoParamThing<String, Integer> twoParamInstance = registry.newInstance(TwoParamThing, String, Integer)

        then:
        twoParamInstance instanceof TwoParamThing
        twoParamInstance.one == String
        twoParamInstance.two == Integer
    }

    def "returns null when there is no creator for the requested type"() {
        def registry = registryOf {
            it.add(ManyProvidingImplementation)
        }

        when:
        def instance = registry.newInstance(String)

        then:
        instance == null

        when:
        instance = registry.newInstance(String, String)

        then:
        instance == null

        when:
        instance = registry.newInstance(String, String, Integer)

        then:
        instance == null
    }

    @ManagedObjectProvider
    static class ThreeArgumentsCreator {

        @ManagedObjectCreator
        Thing createTwoParamThing(Class<?> one, Class<?> two, Class<?> three) {
            throw new UnsupportedOperationException()
        }

    }

    def "creator functions must have two or fewer parameters"() {
        when:
        registryOf {
            it.add(ThreeArgumentsCreator)
        }

        then:
        def e = thrown(RuntimeException)
        e.message == "Method public org.gradle.internal.instantiation.managed.ManagedObjectRegistryTest\$Thing org.gradle.internal.instantiation.managed.ManagedObjectRegistryTest\$ThreeArgumentsCreator.createTwoParamThing(java.lang.Class,java.lang.Class,java.lang.Class) annotated with @ManagedObjectCreator has too many parameters."
    }

    @ManagedObjectProvider
    static class NonClassTypedParameterCreator {

        @ManagedObjectCreator
        <T> OneParamThing<T> createOneParamThing(String foo) {
            throw new UnsupportedOperationException("This should not be called")
        }

    }

    def "creator functions must have class typed parameters"() {
        when:
        registryOf {
            it.add(NonClassTypedParameterCreator)
        }

        then:
        def e = thrown(RuntimeException)
        e.message == "Method public org.gradle.internal.instantiation.managed.ManagedObjectRegistryTest\$OneParamThing org.gradle.internal.instantiation.managed.ManagedObjectRegistryTest\$NonClassTypedParameterCreator.createOneParamThing(java.lang.String) annotated with @ManagedObjectCreator must have parameters of type Class, but has parameter of type class java.lang.String."
    }

    @ManagedObjectProvider
    static class StaticMethodCreator {

        @ManagedObjectCreator
        static Thing createThing() {
            throw new UnsupportedOperationException("This should not be called")
        }

    }

    def "creator functions must not be static"() {
        when:
        registryOf {
            it.add(StaticMethodCreator)
        }

        then:
        def e = thrown(RuntimeException)
        e.message == "Method public static org.gradle.internal.instantiation.managed.ManagedObjectRegistryTest\$Thing org.gradle.internal.instantiation.managed.ManagedObjectRegistryTest\$StaticMethodCreator.createThing() annotated with @ManagedObjectCreator must not be static."
    }

    @ManagedObjectProvider
    static class CreatorFunctionWithoutReturnType {

        @ManagedObjectCreator
        void createThing() {
            // No return type
        }

    }

    def "creator functions must return something"() {
        when:
        registryOf {
            it.add(CreatorFunctionWithoutReturnType)
        }

        then:
        def e = thrown(RuntimeException)
        e.message == "Method public void org.gradle.internal.instantiation.managed.ManagedObjectRegistryTest\$CreatorFunctionWithoutReturnType.createThing() annotated with @ManagedObjectCreator must return a value."
    }

    @ManagedObjectProvider
    static class TwoCreatorsWithSameReturnType {

        @ManagedObjectCreator
        Thing createThing() {
            return new Thing()
        }

        @ManagedObjectCreator
        Thing createAnotherThing(Class<?> arg) {
            return new Thing()
        }

    }

    def "cannot have two creator functions with the same return type"() {
        when:
        registryOf {
            it.add(TwoCreatorsWithSameReturnType)
        }

        then:
        def e = thrown(RuntimeException)
        e.message == "Method MethodHandle()Thing for type class org.gradle.internal.instantiation.managed.ManagedObjectRegistryTest\$Thingconflicts with existing factory method MethodHandle(Class)Thing."
    }

    private static ManagedObjectRegistry registryOf(ServiceRegistrationAction action) {
        def services = ServiceRegistryBuilder.builder().provider {
            it.addProvider(new ServiceRegistrationProvider() {
                @Provides
                ManagedObjectRegistry createManagedObjectRegistry() {
                    return new ManagedObjectRegistry(null)
                }
            })
            action.registerServices(it)
        }.build()

        return services.get(ManagedObjectRegistry)
    }
}
