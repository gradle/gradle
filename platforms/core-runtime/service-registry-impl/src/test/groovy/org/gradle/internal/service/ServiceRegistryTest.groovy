/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.service

import org.gradle.util.GroovyNullMarked
import spock.lang.Specification

class ServiceRegistryTest extends Specification implements ServiceRegistryFixture {

    def "fails when inheriting from DefaultServiceRegistry"() {
        when:
        new DefaultServiceRegistry() {}

        then:
        IllegalArgumentException e = thrown()
        e.message == "Inheriting from DefaultServiceRegistry is not allowed. Use ServiceRegistryBuilder instead."
    }

    def "fails when requesting unknown service"() {
        def registry = newRegistry()

        when:
        registry.get(StringBuilder.class)

        then:
        UnknownServiceException e = thrown()
        e.message == "No service of type StringBuilder available in test registry."
    }

    def "returns service instance that has been registered"() {
        def value = BigDecimal.TEN
        def registry = new DefaultServiceRegistry()

        given:
        registry.add(BigDecimal, value)

        expect:
        registry.get(BigDecimal) == value
        registry.get(Number) == value
    }

    def "does not support querying for Object.class"() {
        def registry = new DefaultServiceRegistry()
        when:
        registry.get(Object)

        then:
        ServiceValidationException e = thrown()
        e.message == "Locating services with type Object is not supported."
    }

    def "creates instance of service implementation"() {
        def registry = new DefaultServiceRegistry()
        registry.register({ ServiceRegistration registration ->
            registration.add(TestServiceImpl)
        })

        expect:
        registry.get(TestService) instanceof TestServiceImpl
        registry.get(TestService) == registry.get(TestServiceImpl)
    }

    def "injects services into service implementation"() {
        def registry = new DefaultServiceRegistry()
        registry.register({ ServiceRegistration registration ->
            registration.add(ServiceWithDependency)
            registration.add(TestServiceImpl)
        })

        expect:
        registry.get(ServiceWithDependency).service == registry.get(TestServiceImpl)
    }

    def "creates service using provider factory method"() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            Integer createInt() {
                return 12
            }
        })

        expect:
        registry.get(Integer) == 12
        registry.get(Number) == 12
    }

    def "injects services into provider factory method"() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            Integer createInteger() {
                return 12
            }

            @Provides
            String createString(Integer integer) {
                return integer.toString()
            }
        })

        expect:
        registry.get(String) == "12"
    }

    def "fails when provider factory method requires unknown service"() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new StringProvider())

        when:
        registry.get(String)

        then:
        ServiceCreationException e = thrown()
        normalizedMessage(e) == "Cannot create service of type String using method StringProvider.createString() as required service of type Runnable for parameter #1 is not available."

        when:
        registry.get(Number)

        then:
        ServiceCreationException e2 = thrown()
        normalizedMessage(e2) == "Cannot create service of type Integer using method StringProvider.createInteger() as there is a problem with parameter #1 of type String."
        normalizedMessage(e2.cause) == "Cannot create service of type String using method StringProvider.createString() as required service of type Runnable for parameter #1 is not available."
    }

    def "fails when constructor requires unknown service"() {
        def registry = new DefaultServiceRegistry()
        registry.register {
            it.add(RequiresService)
        }

        when:
        registry.get(RequiresService)

        then:
        ServiceCreationException e = thrown()
        normalizedMessage(e) == "Cannot create service of type RequiresService using RequiresService constructor as required service of type Number for parameter #1 is not available."
    }

    def "fails when provider factory method throws exception"() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new BrokenProvider())

        when:
        registry.get(String)

        then:
        ServiceCreationException e = thrown()
        e.message == "Could not create service of type String using BrokenProvider.createString()."
        e.cause == BrokenProvider.failure

        when:
        registry.get(Number)

        then:
        e = thrown()
        e.message == "Could not create service of type String using BrokenProvider.createString()."
        e.cause == BrokenProvider.failure
    }

    def "fails when create method has no annotation"() {
        def registry = new DefaultServiceRegistry()

        when:
        registry.addProvider(new UnannotatedServiceProvider())

        then:
        ServiceValidationException e = thrown()
        e.message == "Method ${UnannotatedServiceProvider.name}.createWithoutAnnotation() must be annotated with @Provides."
    }

    private static class UnannotatedServiceProvider implements ServiceRegistrationProvider {
        @SuppressWarnings('unused')
        String createWithoutAnnotation() {
            return "no-annotation"
        }
    }

    def "fails when non create method has annotation"() {
        def registry = new DefaultServiceRegistry()

        when:
        registry.addProvider(new AnnotatedNonServiceProvider())

        then:
        ServiceValidationException e = thrown()
        e.message == "Non-factory method ${AnnotatedNonServiceProvider.name}.factoryWithAnnotation() must not be annotated with @Provides."
    }

    private static class AnnotatedNonServiceProvider implements ServiceRegistrationProvider {
        @Provides
        String factoryWithAnnotation() {
            return "bad-method-name"
        }
    }

    def "fails when interface is registered"() {
        def registry = new DefaultServiceRegistry()
        when:
        registry.register {
            it.add(Runnable)
        }

        then:
        def e = thrown(ServiceValidationException)
        e.message == "Cannot register an interface (java.lang.Runnable) for construction."
    }

    def "fails when abstract class is registered"() {
        def registry = new DefaultServiceRegistry()
        when:
        registry.register {
            it.add(AbstractClass)
        }

        then:
        def e = thrown(ServiceValidationException)
        normalizedMessage(e) == "Cannot register an abstract type (AbstractClass) for construction."
    }

    def "fails when there is a cycle in dependencies for provider factory methods"() {
        def registry = new DefaultServiceRegistry()

        given:
        registry.addProvider(new ProviderWithCycle())

        when:
        registry.get(String)

        then:
        ServiceCreationException e = thrown()
        normalizedMessage(e) == 'Cannot create service of type String using method ProviderWithCycle.createString() as there is a problem with parameter #1 of type Integer.'
        normalizedMessage(e.cause) == 'Cannot create service of type Integer using method ProviderWithCycle.createInteger() as there is a problem with parameter #1 of type String.'
        normalizedMessage(e.cause.cause) == 'Cycle in dependencies of Service String via ProviderWithCycle.createString() detected'

        when:
        registry.getAll(Number)

        then:
        e = thrown(ServiceCreationException)
        normalizedMessage(e) == 'Cannot create service of type Integer using method ProviderWithCycle.createInteger() as there is a problem with parameter #1 of type String.'
        normalizedMessage(e.cause) == 'Cannot create service of type String using method ProviderWithCycle.createString() as there is a problem with parameter #1 of type Integer.'
        normalizedMessage(e.cause.cause) == 'Cycle in dependencies of Service Integer via ProviderWithCycle.createInteger() detected'
    }

    def "fails when a provider factory method returns null"() {
        def registry = new DefaultServiceRegistry()

        given:
        registry.addProvider(new NullProvider())

        when:
        registry.get(String)

        then:
        ServiceCreationException e = thrown()
        e.message == "Could not create service of type String using NullProvider.createString() as this method returned null."
    }

    def "fails when a provider decorator create method returns null"() {
        def parentProvider = Stub(ServiceProvider) {
            getService(String, _) >> new ServiceWrapper("parent")
        }
        def registry = new DefaultServiceRegistry(parentRegistry(parentProvider))

        given:
        registry.addProvider(decoratorProvider)

        when:
        registry.get(String)

        then:
        ServiceCreationException e = thrown()
        e.message == "Could not create service of type String using ${decoratorProvider.class.simpleName}.${methodName}String() as this method returned null."

        where:
        decoratorProvider               | methodName
        new NullDecoratorWithCreate()   | 'create'
        new NullDecoratorWithDecorate() | 'decorate'
    }

    def "creates service with dependencies using provider factory method"() {
        def registry = newRegistry()
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            Integer createInt() {
                return 12
            }

            @Provides
            String createString(Integer integer) {
                return integer.toString()
            }
        })

        expect:
        registry.get(String.class) == "12"
        registry.get(Integer.class) == 12
    }


    def "uses overridden factory method to create service instance"() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            String createString() {
                return "overridden"
            }
        })

        expect:
        registry.get(String) == "overridden"
    }

    def "fails when multiple factory methods can create requested service type"() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new MultiComparableProvider())

        when:
        registry.get(Comparable)

        then:
        ServiceLookupException e = thrown()
        normalizedMessage(e) == """Multiple services of type Comparable available in DefaultServiceRegistry:
   - Service Integer via MultiComparableProvider.createInt()
   - Service String via MultiComparableProvider.createString()"""
    }

    def "fails when multiple factory methods can create requested service type via constructor"() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new ServiceRegistrationProvider() {
            @SuppressWarnings('unused')
            void configure(ServiceRegistration registration) {
                registration.add(TestServiceImpl)
                registration.add(TestServiceImpl2)
            }
        })

        when:
        registry.get(TestService)

        then:
        def e = thrown(ServiceLookupException)
        normalizedMessage(e) == """Multiple services of type TestService available in DefaultServiceRegistry:
   - Service TestServiceImpl via TestServiceImpl constructor
   - Service TestServiceImpl2 via TestServiceImpl2 constructor"""
    }

    def "fails when requesting array class"() {
        def registry = newRegistry()

        when:
        registry.get(String[].class)

        then:
        ServiceLookupException e = thrown()
        e.message == "Locating services with array type is not supported."
    }

    def "fails when injecting an array type"() {
        def registry = newRegistry()

        given:
        registry.addProvider(new UnsupportedInjectionProvider())

        when:
        registry.get(Number)

        then:
        ServiceCreationException e = thrown()
        normalizedMessage(e) == "Cannot create service of type Number using method UnsupportedInjectionProvider.create() as there is a problem with parameter #1 of type String[]."
        e.cause.message == 'Locating services with array type is not supported.'
    }

    def "can register services using action"() {
        def registry = new DefaultServiceRegistry()

        given:
        registry.register({ ServiceRegistration registration ->
            registration.add(Number, 12)
            registration.add(TestServiceImpl)
            registration.addProvider(new ServiceRegistrationProvider() {
                @Provides
                String createString() {
                    return "hi"
                }
            })
        })

        expect:
        registry.get(Number) == 12
        registry.get(TestServiceImpl)
        registry.get(String) == "hi"
    }

    def "provider configure method can register services"() {
        def registry = new DefaultServiceRegistry()

        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            void configure(ServiceRegistration registration, Number value) {
                registration.addProvider(new ServiceRegistrationProvider() {
                    @Provides
                    String createString() {
                        return value.toString()
                    }
                })
            }

            @Provides
            Integer createNumber() {
                return 123
            }
        })

        expect:
        registry.get(Number) == 123
        registry.get(String) == "123"
    }

    def "fails when provider configure method requires unknown service"() {
        def registry = new DefaultServiceRegistry()

        when:
        registry.addProvider(new NoOpConfigureProvider())

        then:
        ServiceLookupException e = thrown()
        e.message == 'Cannot configure services using NoOpConfigureProvider.configure() as required service of type String is not available.'
    }

    def "fails when provider configure method fails"() {
        def registry = new DefaultServiceRegistry()

        when:
        registry.addProvider(new BrokenConfigureProvider())

        then:
        ServiceLookupException e = thrown()
        e.message == 'Could not configure services using BrokenConfigureProvider.configure().'
        e.cause == BrokenConfigureProvider.failure
    }

    def "fails when cannot create service instance from implementation class"() {
        def registry = newRegistry()

        given:
        registry.register({ registration -> registration.add(ClassWithBrokenConstructor) })

        when:
        registry.get(ClassWithBrokenConstructor)

        then:
        ServiceCreationException e = thrown()
        normalizedMessage(e) == 'Could not create service of type ClassWithBrokenConstructor.'
        e.cause == ClassWithBrokenConstructor.failure
    }

    def "can lookup a service with declared service type added via registration"() {
        def registry = newRegistry()

        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            void configure(ServiceRegistration registration) {
                registration.add(TestService, TestServiceImpl)
            }
        })

        when:
        def service = registry.get(TestService)
        then:
        service instanceof TestService
    }

    def "cannot lookup implementation of a service with declared service type added via registration"() {
        def registry = newRegistry()

        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            void configure(ServiceRegistration registration) {
                registration.add(TestService, TestServiceImpl)
            }
        })

        when:
        registry.get(TestServiceImpl)
        then:
        def e = thrown(UnknownServiceException)
        normalizedMessage(e) == "No service of type TestServiceImpl available in test registry."
    }

    def "cannot declare explicit service type via @Provides that is not implemented by the return type"() {
        def registry = newRegistry()

        when:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides([Runnable])
            TestServiceImpl create() { new TestServiceImpl() }
        })

        then:
        def e = thrown(ServiceValidationException)
        e.message == "Cannot register implementation 'TestServiceImpl' for service 'Runnable', because it does not implement it"
    }

    private AbstractServiceRegistry parentRegistry(ServiceProvider provider) {
        def parent = Mock(AbstractServiceRegistry)
        parent.asServiceProvider() >> provider
        return parent
    }

    @GroovyNullMarked
    private static class ServiceWrapper implements Service {
        private final Object instance

        ServiceWrapper(Object instance) {
            this.instance = instance
        }

        @Override
        String getDisplayName() {
            throw new UnsupportedOperationException()
        }

        @Override
        Object get() {
            return instance
        }

        @Override
        void requiredBy(ServiceProvider serviceProvider) {
        }
    }

    private interface TestService {
    }

    private static class TestServiceImpl implements TestService {
    }

    private static class TestServiceImpl2 implements TestService {
    }

    private static class ServiceWithDependency {
        final TestService service

        ServiceWithDependency(TestService service) {
            this.service = service
        }
    }

    private static class MultiComparableProvider implements ServiceRegistrationProvider {
        @Provides
        Integer createInt() {
            return 12
        }

        @Provides
        String createString() {
            return "hello"
        }
    }

    private static class StringProvider implements ServiceRegistrationProvider {
        @Provides
        String createString(Runnable r) {
            return "hi"
        }

        @Provides
        Integer createInteger(String value) {
            return value.length()
        }
    }

    private static class ProviderWithCycle implements ServiceRegistrationProvider {
        @Provides
        String createString(Integer value) {
            return value.toString()
        }

        @Provides
        Integer createInteger(String value) {
            return value.length()
        }
    }

    private static class NullProvider implements ServiceRegistrationProvider {
        @Provides
        String createString() {
            return null
        }
    }

    private static class UnsupportedInjectionProvider implements ServiceRegistrationProvider {
        @Provides
        Number create(String[] values) {
            return values.length
        }
    }

    private static class NoOpConfigureProvider implements ServiceRegistrationProvider {
        void configure(ServiceRegistration registration, String value) {
        }
    }

    private static class BrokenConfigureProvider implements ServiceRegistrationProvider {
        static def failure = new RuntimeException()

        void configure(ServiceRegistration registration) {
            throw failure
        }
    }

    private static class BrokenProvider implements ServiceRegistrationProvider {
        static def failure = new RuntimeException()

        @Provides
        String createString() {
            throw failure.fillInStackTrace()
        }

        @Provides
        Integer createInteger(String value) {
            return value.length()
        }
    }

    private static class NullDecoratorWithCreate implements ServiceRegistrationProvider {
        @Provides
        String createString(String value) {
            return null
        }
    }

    private static class NullDecoratorWithDecorate implements ServiceRegistrationProvider {
        @Provides
        String decorateString(String value) {
            return null
        }
    }

    static abstract class AbstractClass {

    }

    static class ClassWithBrokenConstructor {
        static def failure = new RuntimeException("broken")

        ClassWithBrokenConstructor() {
            throw failure
        }
    }

    static class RequiresService {
        RequiresService(Number value) {
        }
    }
}
