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

import org.gradle.internal.Factory
import org.gradle.internal.service.stubs.ProviderWithGenericType
import org.gradle.util.GroovyNullMarked
import org.gradle.util.internal.TextUtil
import spock.lang.Specification

import java.util.concurrent.Callable

class ServiceRegistryTest extends Specification {

    def registry = new DefaultServiceRegistry("test registry")
        .addProvider(new TestProvider())

    def notAllowedToInherit() {
        when:
        new DefaultServiceRegistry() {}

        then:
        IllegalArgumentException e = thrown()
        e.message == "Inheriting from DefaultServiceRegistry is not allowed. Use ServiceRegistryBuilder instead."
    }

    // tags: error, look-up
    def throwsExceptionForUnknownService() {
        when:
        registry.get(StringBuilder.class)

        then:
        UnknownServiceException e = thrown()
        e.message == "No service of type StringBuilder available in test registry."
    }



    // tags: basic, internal-api
    def returnsServiceInstanceThatHasBeenRegistered() {
        def value = BigDecimal.TEN
        def registry = new DefaultServiceRegistry()

        given:
        registry.add(BigDecimal, value)

        expect:
        registry.get(BigDecimal) == value
        registry.get(Number) == value
    }

    // tags: error, look-up
    def "does not support querying for Object.class"() {
        def registry = new DefaultServiceRegistry()
        when:
        registry.get(Object)

        then:
        ServiceValidationException e = thrown()
        e.message == "Locating services with type Object is not supported."
    }

    // tags: basic, registration
    def createsInstanceOfServiceImplementation() {
        def registry = new DefaultServiceRegistry()
        registry.register({ ServiceRegistration registration ->
            registration.add(TestServiceImpl)
        })

        expect:
        registry.get(TestService) instanceof TestServiceImpl
        registry.get(TestService) == registry.get(TestServiceImpl)
    }

    // tags: basic, service-dependencies
    def injectsServicesIntoServiceImplementation() {
        def registry = new DefaultServiceRegistry()
        registry.register({ ServiceRegistration registration ->
            registration.add(ServiceWithDependency)
            registration.add(TestServiceImpl)
        })

        expect:
        registry.get(ServiceWithDependency).service == registry.get(TestServiceImpl)
    }

    // tags: basic, registration
    def usesFactoryMethodOnProviderToCreateServiceInstance() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new TestProvider())

        expect:
        registry.get(Integer) == 12
        registry.get(Number) == 12
    }

    // tags: service-dependencies
    def injectsServicesIntoProviderFactoryMethod() {
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

    // tags: service-dependencies, generics
    def injectsGenericTypesIntoProviderFactoryMethod() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            Integer createInteger(Factory<String> factory) {
                return factory.create().length()
            }

            @Provides
            Factory<String> createString(Callable<String> action) {
                return { action.call() } as Factory
            }

            @Provides
            Callable<String> createAction() {
                return { "hi" }
            }
        })

        expect:
        registry.get(Integer) == 2
    }

    // tags: service-dependencies, generics
    def handlesInheritanceInGenericTypes() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new ProviderWithGenericType())

        expect:
        registry.get(Integer) == 123
    }

    // tags: service-dependencies, generics
    def canHaveMultipleServicesWithParameterizedTypesAndSameRawType() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            Integer createInteger(Callable<Integer> factory) {
                return factory.call()
            }

            @Provides
            String createString(Callable<String> factory) {
                return factory.call()
            }

            @Provides
            Callable<Integer> createIntFactory() {
                return { 123 }
            }

            @Provides
            Callable<String> createStringFactory() {
                return { "hi" }
            }
        })

        expect:
        registry.get(Integer) == 123
        registry.get(String) == "hi"
    }


    // tags: self-injection, service-dependencies
    def injectsServiceRegistryIntoProviderFactoryMethod() {
        def parentProvider = Mock(ServiceProvider)
        def registry = new DefaultServiceRegistry(parentRegistry(parentProvider))
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            String createString(ServiceRegistry services) {
                assert services.is(registry)
                return services.get(Number).toString()
            }
        })
        registry.add(Integer, 123)

        expect:
        registry.get(String) == '123'
    }

    // tags: self-injection, service-dependencies
    def canLocateSelfAsAServiceOfTypeServiceRegistry() {
        def registry = new DefaultServiceRegistry()

        expect:
        registry.get(ServiceRegistry) == registry
        registry.find(DefaultServiceRegistry) == null
    }

    // tags: self-injection, registration, error
    def failsWhenRegisteringAServiceOfTypeServiceRegistry() {
        def registry = new DefaultServiceRegistry()

        when:
        registry.add(ServiceRegistry, new DefaultServiceRegistry())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Cannot define a service of type ServiceRegistry: Service ServiceRegistry with implementation DefaultServiceRegistry'
    }

    // tags: self-injection, registration, error
    def failsWhenProviderFactoryMethodProducesAServiceOfTypeServiceRegistry() {
        def registry = new DefaultServiceRegistry()

        when:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            ServiceRegistry createServices() {
                return new DefaultServiceRegistry()
            }
        })

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Cannot define a service of type ServiceRegistry: Service ServiceRegistry via ServiceRegistryTest$<anonymous>.createServices()'
    }

    // tags: service-dependencies, error
    def failsWhenProviderFactoryMethodRequiresUnknownService() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new StringProvider())

        when:
        registry.get(String)

        then:
        ServiceCreationException e = thrown()
        e.message == "Cannot create service of type String using method ServiceRegistryTest\$StringProvider.createString() as required service of type Runnable for parameter #1 is not available."

        when:
        registry.get(Number)

        then:
        e = thrown()
        e.message == "Cannot create service of type Integer using method ServiceRegistryTest\$StringProvider.createInteger() as there is a problem with parameter #1 of type String."
        e.cause.message == "Cannot create service of type String using method ServiceRegistryTest\$StringProvider.createString() as required service of type Runnable for parameter #1 is not available."
    }

    // tags: service-dependencies, error
    def failsWhenConstructorRequiresUnknownService() {
        def registry = new DefaultServiceRegistry()
        registry.register {
            it.add(RequiresService)
        }

        when:
        registry.get(RequiresService)

        then:
        ServiceCreationException e = thrown()
        e.message == "Cannot create service of type ServiceRegistryTest\$RequiresService using ServiceRegistryTest\$RequiresService constructor as required service of type Number for parameter #1 is not available."
    }

    // tags: service-dependencies, instantiation, error
    def failsWhenProviderFactoryMethodThrowsException() {
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

    // tags: registration, error
    def failsWhenCreateMethodHasNoAnnotation() {
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

    // tags: registration, error
    def failsWhenNonCreateMethodHasAnnotation() {
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

    // tags: registration, error
    def failsWhenInterfaceIsRegistered() {
        def registry = new DefaultServiceRegistry()
        when:
        registry.register {
            it.add(Runnable)
        }

        then:
        def e = thrown(ServiceValidationException)
        e.message == "Cannot register an interface (java.lang.Runnable) for construction."
    }

    // tags: registration, error
    def "fails when abstract class is registered"() {
        def registry = new DefaultServiceRegistry()
        when:
        registry.register {
            it.add(AbstractClass)
        }

        then:
        def e = thrown(ServiceValidationException)
        e.message == "Cannot register an abstract type (org.gradle.internal.service.ServiceRegistryTest.AbstractClass) for construction."
    }

    // tags: lifecycle
    def cachesInstancesCreatedUsingAProviderFactoryMethod() {
        def registry = new DefaultServiceRegistry()
        def provider = new ServiceRegistrationProvider() {
            @Provides
            String createString(Number number) {
                return number.toString()
            }

            @Provides
            Integer createInteger() {
                return 12
            }
        }
        registry.addProvider(provider)

        expect:
        registry.get(Integer).is(registry.get(Integer))
        registry.get(Number).is(registry.get(Number))

        and:
        registry.get(String).is(registry.get(String))
    }


    // tags: service-dependencies, creation, error
    def failsWhenThereIsACycleInDependenciesForProviderFactoryMethods() {
        def registry = new DefaultServiceRegistry()

        given:
        registry.addProvider(new ProviderWithCycle())

        when:
        registry.get(String)

        then:
        ServiceCreationException e = thrown()
        e.message == 'Cannot create service of type String using method ServiceRegistryTest$ProviderWithCycle.createString() as there is a problem with parameter #1 of type Integer.'
        e.cause.message == 'Cannot create service of type Integer using method ServiceRegistryTest$ProviderWithCycle.createInteger() as there is a problem with parameter #1 of type String.'
        e.cause.cause.message == 'Cycle in dependencies of Service String via ServiceRegistryTest$ProviderWithCycle.createString() detected'

        when:
        registry.getAll(Number)

        then:
        e = thrown()

        e.message == 'Cannot create service of type Integer using method ServiceRegistryTest$ProviderWithCycle.createInteger() as there is a problem with parameter #1 of type String.'
        e.cause.message == 'Cannot create service of type String using method ServiceRegistryTest$ProviderWithCycle.createString() as there is a problem with parameter #1 of type Integer.'
        e.cause.cause.message == 'Cycle in dependencies of Service Integer via ServiceRegistryTest$ProviderWithCycle.createInteger() detected'
    }

    // tags: creation, error
    def failsWhenAProviderFactoryMethodReturnsNull() {
        def registry = new DefaultServiceRegistry()

        given:
        registry.addProvider(new NullProvider())

        when:
        registry.get(String)

        then:
        ServiceCreationException e = thrown()
        e.message == "Could not create service of type String using NullProvider.createString() as this method returned null."
    }

    // tags: creation, error
    def failsWhenAProviderDecoratorCreateMethodReturnsNull() {
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

    // tags: basic, look-up
    def usesFactoryMethodToCreateServiceInstance() {
        expect:
        registry.get(String.class) == "12"
        registry.get(Integer.class) == 12
    }

    // tags: basic, lifecycle
    def cachesInstancesCreatedUsingAFactoryMethod() {
        expect:
        registry.get(Integer).is(registry.get(Integer))
        registry.get(Number).is(registry.get(Number))
    }

    // tags: registration
    def usesOverriddenFactoryMethodToCreateServiceInstance() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new OverridingTestProvider())

        expect:
        registry.get(String) == "overridden"
    }

    // tags: registration, creation, error
    def failsWhenMultipleFactoryMethodsCanCreateRequestedServiceType() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new TestProvider())

        when:
        registry.get(Comparable)

        then:
        ServiceLookupException e = thrown()
        e.message == TextUtil.toPlatformLineSeparators("""Multiple services of type Comparable available in DefaultServiceRegistry:
   - Service Integer via ServiceRegistryTest\$TestProvider.createInt()
   - Service String via ServiceRegistryTest\$TestProvider.createString()""")
    }

    // tags: registration, creation, error
    def failsWhenMultipleFactoryMethodsCanCreateRequestedServiceTypeViaConstructor() {
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
        withoutTestClassName(e.message) == TextUtil.toPlatformLineSeparators("""Multiple services of type TestService available in DefaultServiceRegistry:
   - Service TestServiceImpl via TestServiceImpl constructor
   - Service TestServiceImpl2 via TestServiceImpl2 constructor""")
    }

    // tags: look-up, error
    def failsWhenArrayClassRequested() {
        when:
        registry.get(String[].class)

        then:
        ServiceLookupException e = thrown()
        e.message == "Locating services with array type is not supported."
    }

    // tags: registration, error
    def cannotInjectAnArrayType() {
        given:
        registry.addProvider(new UnsupportedInjectionProvider())

        when:
        registry.get(Number)

        then:
        ServiceCreationException e = thrown()
        e.message == "Cannot create service of type Number using method ServiceRegistryTest\$UnsupportedInjectionProvider.create() as there is a problem with parameter #1 of type String[]."
        e.cause.message == 'Locating services with array type is not supported.'
    }

    // tags: registration
    def canRegisterServicesUsingAction() {
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

    def providerConfigureMethodCanRegisterServices() {
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

    def failsWhenProviderConfigureMethodRequiresUnknownService() {
        def registry = new DefaultServiceRegistry()

        when:
        registry.addProvider(new NoOpConfigureProvider())

        then:
        ServiceLookupException e = thrown()
        e.message == 'Cannot configure services using NoOpConfigureProvider.configure() as required service of type String is not available.'
    }

    def failsWhenProviderConfigureMethodFails() {
        def registry = new DefaultServiceRegistry()

        when:
        registry.addProvider(new BrokenConfigureProvider())

        then:
        ServiceLookupException e = thrown()
        e.message == 'Could not configure services using BrokenConfigureProvider.configure().'
        e.cause == BrokenConfigureProvider.failure
    }

    def failsWhenCannotCreateServiceInstanceFromImplementationClass() {
        given:
        registry.register({ registration -> registration.add(ClassWithBrokenConstructor) })

        when:
        registry.get(ClassWithBrokenConstructor)

        then:
        ServiceCreationException e = thrown()
        e.message == 'Could not create service of type ServiceRegistryTest$ClassWithBrokenConstructor.'
        e.cause == ClassWithBrokenConstructor.failure
    }

    def "can lookup a service with declared service type added via registration"() {
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
        e.message == "No service of type ServiceRegistryTest\$TestServiceImpl available in test registry."
    }

    def "cannot declare explicit service type via @Provides that is not implemented by the return type"() {
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

    private static class TestFactory implements Factory<BigDecimal> {
        int value

        BigDecimal create() {
            return BigDecimal.valueOf(value++)
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

    private static class TestProvider implements ServiceRegistrationProvider {
        @Provides
        String createString(Integer integer) {
            return integer.toString()
        }

        @Provides
        Integer createInt() {
            return 12
        }

        @Provides
        Factory<BigDecimal> createTestFactory() {
            return new TestFactory()
        }

        @Provides
        Callable<BigDecimal> createCallable() {
            return { 12 }
        }
    }

    private static class OverridingTestProvider extends TestProvider {
        @Override
        @Provides
        String createString(Integer integer) {
            return "overridden"
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

    private String withoutTestClassName(String s) {
        s.replaceAll(this.class.simpleName + "\\\$", "")
    }
}
