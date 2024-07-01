/*
 * Copyright 2024 the original author or authors.
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

import spock.lang.Specification

class DefaultServiceRegistryFromConstructorInjectionTest extends Specification {

    TestRegistry registry = new TestRegistry()

    def "can inject implementation via constructor"() {
        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestService create(@FromConstructor TestServiceImpl service) { service }
        })

        when:
        def service1 = registry.get(TestService)
        then:
        service1 instanceof TestServiceImpl

        when:
        registry.get(TestServiceImpl)
        then:
        def e = thrown(UnknownServiceException)
        withoutTestClassName(e.message) == "No service of type TestServiceImpl available in TestRegistry."
    }

    def "can inject implementation with dependency via constructor"() {
        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestService createTestService() { new TestServiceImpl() }

            @Provides
            ServiceWithDependency create(@FromConstructor ServiceWithDependency service) { service }
        })

        when:
        def service1 = registry.get(ServiceWithDependency)
        then:
        service1 instanceof ServiceWithDependency
    }

    def "can inject overloads when implementations are injected via constructor"() {
        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestService create(@FromConstructor TestServiceImpl service) { service }

            @Provides
            ServiceWithDependency create(@FromConstructor ServiceWithDependency service) { service }
        })

        when:
        def service1 = registry.get(TestService)
        then:
        service1 instanceof TestService

        when:
        def service2 = registry.get(ServiceWithDependency)
        then:
        service2 instanceof ServiceWithDependency
    }

    def "cannot inject implementation with a missing dependency via constructor"() {
        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            ServiceWithDependency create(@FromConstructor ServiceWithDependency service) { service }
        })

        when:
        registry.get(ServiceWithDependency)
        then:
        def e = thrown(ServiceCreationException)
        withoutTestClassName(e.message) ==
            "Cannot create service of type ServiceWithDependency using ServiceWithDependency constructor as required service of type TestService for parameter #1 is not available."
    }

    def "can inject dependencies when using injection via constructor"() {
        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            Integer createInt() { 12 }

            @Provides
            TestService create(@FromConstructor StatefulTestServiceImpl service, Integer dependency) {
                service.value = dependency
                service
            }
        })

        when:
        def service1 = registry.get(TestService) as StatefulTestServiceImpl
        then:
        service1.value == 12
    }

    def "can inject multiple implementation instances via constructor in the same factory method"() {
        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            ServiceWithDependency create(@FromConstructor TestServiceImpl service1, @FromConstructor TestServiceImpl service2) {
                if (service1 == service2) {
                    throw new IllegalStateException("Unreachable")
                }
                new ServiceWithDependency(service2)
            }
        })

        when:
        def service = registry.get(ServiceWithDependency)
        then:
        service instanceof ServiceWithDependency
    }

    def "can inject independent implementation instances via constructor"() {
        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestService create1(@FromConstructor TestServiceImpl service) { service }

            @Provides
            TestService create2(@FromConstructor TestServiceImpl service) { service }
        })

        when:
        def services = registry.getAll(TestService)
        then:
        services.size() == 2
        services[0] instanceof TestServiceImpl
        services[1] instanceof TestServiceImpl
        services[0] !== services[1]
    }

    def "cannot inject implementation via constructor in injected constructors"() {
        when:
        registry.addProvider(new ServiceRegistrationProvider() {
            @SuppressWarnings('unused')
            void configure(ServiceRegistration registration) {
                registration.add(ServiceWithConstructorParameterUsingFromConstructor)
            }
        })

        then:
        def e = thrown(ServiceLookupException)
        e.message == "Could not configure services using .configure()."

        def cause = e.cause
        cause instanceof ServiceValidationException
        withoutTestClassName(cause.message) ==
            "Cannot register a constructor with direct service provider injection for type ServiceWithConstructorParameterUsingFromConstructor"
    }

    def "from-constructor services are closed when used as services"() {
        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            CloseableService create(@FromConstructor CloseableService service) { service }

            @Provides
            Integer createInt(CloseableService s) { s.closed + 10 }
        })

        when:
        def service = registry.get(CloseableService)
        then:
        service.closed == 0

        when:
        def dependant = registry.get(Integer)
        then:
        dependant == 10

        when:
        registry.close()
        then:
        service.closed == 1
    }

    def "from-constructor service is not closed if it is not exposed as a service"() {
        given:
        CloseableService capturedService = null
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            Integer create(@FromConstructor CloseableService service) {
                capturedService = service
                service.closed + 10
            }
        })

        when:
        def service = registry.get(Integer)
        then:
        service == 10

        when:
        registry.close()
        then:
        capturedService.closed == 0
    }

    // TODO: annotation handler tests

    private interface TestService {
    }

    private static class TestServiceImpl implements TestService {
    }

    private static class StatefulTestServiceImpl implements TestService {
        int value = 0
    }

    private static class ServiceWithDependency {
        final TestService service

        ServiceWithDependency(TestService service) {
            this.service = service
        }
    }

    private static class TestRegistry extends DefaultServiceRegistry {
        TestRegistry() {
        }
    }

    private static class ServiceWithConstructorParameterUsingFromConstructor extends ServiceWithDependency {
        ServiceWithConstructorParameterUsingFromConstructor(@FromConstructor TestServiceImpl ts) {
            super(ts)
        }
    }

    private static class CloseableService implements Closeable {
        int closed = 0

        void close() {
            closed++
        }
    }

    private String withoutTestClassName(String s) {
        s.replaceAll(this.class.simpleName + "\\\$", "")
    }
}
