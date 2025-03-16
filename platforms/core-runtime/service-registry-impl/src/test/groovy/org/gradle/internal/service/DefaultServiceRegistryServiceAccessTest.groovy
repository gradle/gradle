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

class DefaultServiceRegistryServiceAccessTest extends Specification {

    TestRegistry registry = new TestRegistry()

    def "can use private service as a dependency within the same service provider"() {
        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            @PrivateService
            TestService create() { new TestServiceImpl() }

            @Provides
            ServiceWithDependency create(TestService ts) { new ServiceWithDependency(ts) }
        })

        when:
        def service1 = registry.get(ServiceWithDependency)
        then:
        service1 instanceof ServiceWithDependency

        when:
        registry.get(TestService)
        then:
        def e = thrown(UnknownServiceException)
        withoutTestClassName(e.message) == "No service of type TestService available in TestRegistry."
    }

    def "can use private service as a dependency in constructor-based injection within the same service provider"() {
        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            @PrivateService
            TestService create() { new TestServiceImpl() }

            void configure(ServiceRegistration registration) {
                registration.add(ServiceWithDependency)
            }
        })

        when:
        def service1 = registry.get(ServiceWithDependency)
        then:
        service1 instanceof ServiceWithDependency

        when:
        registry.get(TestService)
        then:
        def e = thrown(UnknownServiceException)
        withoutTestClassName(e.message) == "No service of type TestService available in TestRegistry."
    }

    def "private service is reused within the same service provider"() {
        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            @PrivateService
            TestService create() { new TestServiceImpl() }

            @Provides
            ServiceWithDependency create1(TestService ts) { new ServiceWithDependency(ts) }

            @Provides
            ServiceWithDependency create2(TestService ts) { new ServiceWithDependency(ts) }
        })

        when:
        def services = registry.getAll(ServiceWithDependency)
        then:
        services.size() == 2
        services[0].service === services[1].service
    }

    def "private services are closed when the registry is closed"() {
        given:
        def closeableService = new CloseableService()

        when:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            @PrivateService
            CloseableService create() { closeableService }

            @SuppressWarnings('unused')
            @Provides
            TestService create(CloseableService s) { new TestServiceImpl() }
        })
        then:
        !closeableService.closed

        when:
        def service = registry.get(TestService)
        then:
        service instanceof TestServiceImpl
        !closeableService.closed

        when:
        registry.close()
        then:
        closeableService.closed
    }

    def "cannot lookup services declared as private"() {
        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            @PrivateService
            TestService create() { new TestServiceImpl() }
        })

        when:
        registry.get(TestService)
        then:
        def e = thrown(UnknownServiceException)
        withoutTestClassName(e.message) == "No service of type TestService available in TestRegistry."
    }

    def "cannot use private services as dependencies in sibling providers"() {
        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            @PrivateService
            TestService create() { new TestServiceImpl() }
        })
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            ServiceWithDependency create(TestService ts) { new ServiceWithDependency(ts) }
        })

        when:
        registry.get(ServiceWithDependency)
        then:
        def e = thrown(ServiceCreationException)
        withoutTestClassName(e.message) == "Cannot create service of type ServiceWithDependency using method <anonymous>.create() as required service of type TestService for parameter #1 is not available."
    }

    def "cannot use private services as dependencies in service providers added via configure"() {
        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            @PrivateService
            TestService create() { new TestServiceImpl() }

            void configure(ServiceRegistration registration) {
                registration.addProvider(new ServiceRegistrationProvider() {
                    @Provides
                    ServiceWithDependency create(TestService ts) { new ServiceWithDependency(ts) }
                })
            }
        })

        when:
        registry.get(ServiceWithDependency)
        then:
        def e = thrown(ServiceCreationException)
        withoutTestClassName(e.message) == 'Cannot create service of type ServiceWithDependency using method <anonymous>$<anonymous>.create() as required service of type TestService for parameter #1 is not available.'
    }

    def "cannot use private services as dependencies in child registries"() {
        given:
        def parentRegistry = registry
        parentRegistry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            @PrivateService
            TestService create() { new TestServiceImpl() }
        })

        def registry = new TestRegistry(parentRegistry)
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            ServiceWithDependency create(TestService ts) { new ServiceWithDependency(ts) }
        })

        when:
        registry.get(ServiceWithDependency)
        then:
        def e = thrown(ServiceCreationException)
        withoutTestClassName(e.message) == "Cannot create service of type ServiceWithDependency using method <anonymous>.create() as required service of type TestService for parameter #1 is not available."
    }

    def "can collect private services from the same service provider"() {
        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            @PrivateService
            Integer create1() { 1 }

            @Provides
            @PrivateService
            Integer create2() { 2 }

            @Provides
            Integer create3() { 3 }

            @Provides
            String create(List<Integer> list) { list.toSorted().join("-") }
        })

        when:
        def service = registry.get(String)
        then:
        service == "1-2-3"
    }

    def "private services are not collected with external getAll"() {
        given:
        def parentRegistry = registry
        parentRegistry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            @PrivateService
            Integer create1() { 1 }

            @Provides
            Integer create2() { 2 }
        })

        def registry = new TestRegistry(parentRegistry)
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            @PrivateService
            Integer create1() { 3 }

            @Provides
            Integer create2() { 4 }

            void configure(ServiceRegistration registration) {
                registration.addProvider(new ServiceRegistrationProvider() {
                    @Provides
                    @PrivateService
                    Integer create1() { 5 }

                    @Provides
                    Integer create2() { 6 }
                })
            }
        })

        when:
        def services = registry.getAll(Integer)
        then:
        services.toSorted() == [2, 4, 6]
    }

    def "can declare a private service together with a private one"() {
        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            @PrivateService
            Integer create1() { 1 }

            @Provides
            Integer create2() { 2 }
        })

        when:
        def result = registry.get(Integer)
        then:
        result == 2
    }

    def "private and non-private service declarations conflict for a dependency"() {
        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            @PrivateService
            Integer create1() { 1 }

            @Provides
            Integer create2() { 2 }

            @Provides
            TestService create(Integer i) { throw new IllegalStateException("Unreachable") }
        })

        when:
        registry.get(TestService)
        then:
        def e = thrown(ServiceCreationException)
        withoutTestClassName(e.message) == 'Cannot create service of type TestService using method <anonymous>.create() as there is a problem with parameter #1 of type Integer.'
        def cause = e.cause
        cause instanceof ServiceLookupException
        withoutTestClassName(cause.message).contains('Multiple services of type Integer available in TestRegistry:')
        withoutTestClassName(cause.message).contains('- Service Integer via <anonymous>.create1()')
        withoutTestClassName(cause.message).contains('- Service Integer via <anonymous>.create2()')
    }

    private interface TestService {
    }

    private static class TestServiceImpl implements TestService {
    }

    private static class ServiceWithDependency {
        final TestService service

        ServiceWithDependency(TestService service) {
            this.service = service
        }
    }

    static class CloseableService implements Closeable {
        boolean closed

        void close() {
            closed = true
        }
    }

    private static class TestRegistry extends DefaultServiceRegistry {
        TestRegistry() {
        }

        TestRegistry(ServiceRegistry parent) {
            super(parent)
        }
    }

    private String withoutTestClassName(String s) {
        s.replaceAll(this.class.simpleName + "\\\$", "")
    }
}
