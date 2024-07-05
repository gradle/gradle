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

class DefaultServiceRegistryLazyServiceTest extends Specification {

    TestRegistry registry = new TestRegistry()

    def "lazy dependencies are not created until requested"() {
        given:
        def lazyServiceImpl = new TestServiceImpl()
        lazyServiceImpl.value = 10
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestService create() {
                lazyServiceImpl.value = 20
                lazyServiceImpl
            }

            @Provides
            ServiceWithLazyDependency create(LazyService<TestService> ts) { new ServiceWithLazyDependency(ts) }
        })

        when:
        def serviceWithDependency = registry.get(ServiceWithLazyDependency)
        then:
        serviceWithDependency.lazyService != null
        lazyServiceImpl.value == 10

        when:
        def actualLazyService = serviceWithDependency.lazyService.instance
        then:
        actualLazyService === lazyServiceImpl
        lazyServiceImpl.value == 20
    }

    def "lazy dependencies can be injected into constructors"() {
        given:
        def lazyServiceImpl = new TestServiceImpl()
        lazyServiceImpl.value = 10
        registry.addProvider(new ServiceRegistrationProvider() {
            @SuppressWarnings('unused')
            void configure(ServiceRegistration registration) {
                registration.add(ServiceWithLazyDependency)
            }

            @Provides
            TestService create() {
                lazyServiceImpl.value = 20
                lazyServiceImpl
            }
        })

        when:
        def serviceWithDependency = registry.get(ServiceWithLazyDependency)
        then:
        serviceWithDependency.lazyService != null
        lazyServiceImpl.value == 10

        when:
        def actualLazyService = serviceWithDependency.lazyService.instance
        then:
        actualLazyService === lazyServiceImpl
        lazyServiceImpl.value == 20
    }

    def "lazy services are reused"() {
        given:
        def lazyServiceImpl = new TestServiceImpl()
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestService create() {
                lazyServiceImpl
            }

            @Provides
            ServiceWithLazyDependency create1(LazyService<TestService> ts) { new ServiceWithLazyDependency(ts) }

            @Provides
            ServiceWithLazyDependency create2(LazyService<TestService> ts) { new ServiceWithLazyDependency(ts) }
        })

        when:
        def services = registry.getAll(ServiceWithLazyDependency)
        then:
        services.size() == 2
        services[0].lazyService.instance === lazyServiceImpl
        services[1].lazyService.instance === lazyServiceImpl
    }

    def "lazy service is unavailable when underlying service is unavailable"() {
        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            ServiceWithLazyDependency create(LazyService<TestService> ts) { unreachable() }
        })

        when:
        registry.get(ServiceWithLazyDependency)
        then:
        def e = thrown(ServiceCreationException)
        withoutTestClassName(e.message) ==
            "Cannot create service of type ServiceWithLazyDependency using method <anonymous>.create() as required service of type LazyService<TestService> for parameter #1 is not available."
    }

    def "lazy service cannot be a return type of a provider method"() {
        when:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            LazyService<TestService> create() { unreachable() }
        })

        then:
        true
        def e = thrown(ServiceValidationException)
        withoutTestClassName(e.message) ==
            "Cannot register service of type LazyService<TestService>, use the actual service type instead"
    }

    def "aggregation of lazy services is not supported"() {
        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            ServiceWithLazyDependency create(List<LazyService<TestService>> services) { unreachable() }
        })

        when:
        registry.get(ServiceWithLazyDependency)
        then:
        def e = thrown(ServiceCreationException)
        withoutTestClassName(e.message) ==
            "Cannot create service of type ServiceWithLazyDependency using method <anonymous>.create() as there is a problem with parameter #1 of type List<LazyService<TestService>>."

        def cause = e.cause as ServiceValidationException
        withoutTestClassName(cause.message) ==
            "Locating services with type LazyService<TestService> is not supported."
    }

    def "lazy services aggregation is not supported"() {
        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            ServiceWithLazyDependency create(LazyService<List<TestService>> services) { unreachable() }
        })

        when:
        registry.get(ServiceWithLazyDependency)
        then:
        def e = thrown(ServiceCreationException)
        withoutTestClassName(e.message) ==
            "Cannot create service of type ServiceWithLazyDependency using method <anonymous>.create() as there is a problem with parameter #1 of type LazyService<List<TestService>>."

        def cause = e.cause as ServiceValidationException
        withoutTestClassName(cause.message) ==
            "Locating services with type List<TestService> is not supported."
    }

    private interface TestService {
    }

    private static class TestServiceImpl implements TestService {
        int value = 0
    }

    private static class ServiceWithLazyDependency {
        LazyService<TestService> lazyService

        ServiceWithLazyDependency(LazyService<TestService> lazyService) {
            this.lazyService = lazyService
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

    private static <T> T unreachable() {
        throw new IllegalStateException("unreachable")
    }
}
