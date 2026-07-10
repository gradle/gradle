/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.internal.concurrent.Stoppable
import spock.lang.Specification

import java.lang.reflect.Type

class ServiceRegistryLifecycleTest extends Specification implements ServiceRegistryFixture {

    def "reuses service instances of services registered via provides-method"() {
        TestService.instancesCreated = 0
        def registry = newRegistry().addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestService createTestService() { new TestService() }
        })

        expect:
        registry.get(TestService).is(registry.get(TestService))
        TestService.instancesCreated == 1
    }

    def "reuses dependency service instances of services registered via provides-method"() {
        TestService.instancesCreated = 0
        def registry = newRegistry().addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestService createTestService() { new TestService() }

            @Provides
            DependentService createDependentService(TestService testService) { new DependentService(testService) }
        })

        expect:
        def dependentService = registry.get(DependentService)
        dependentService.is(registry.get(DependentService))
        dependentService.dependency.is(registry.get(TestService))
        TestService.instancesCreated == 1
    }

    def "reuses service instances of services registered via constructor injection"() {
        TestService.instancesCreated = 0
        def registry = newRegistry().addProvider(new ServiceRegistrationProvider() {
            @SuppressWarnings('unused')
            void configure(ServiceRegistration registration) {
                registration.add(TestService)
            }
        })

        expect:
        registry.get(TestService).is(registry.get(TestService))
        TestService.instancesCreated == 1
    }

    def "reuses dependency service instances of services registered via constructor injection"() {
        TestService.instancesCreated = 0
        def registry = newRegistry().addProvider(new ServiceRegistrationProvider() {
            @SuppressWarnings('unused')
            void configure(ServiceRegistration registration) {
                registration.add(TestService)
                registration.add(DependentService)
            }
        })

        expect:
        def dependentService = registry.get(DependentService)
        dependentService.is(registry.get(DependentService))
        dependentService.dependency.is(registry.get(TestService))
        TestService.instancesCreated == 1
    }

    def "close invokes close method on each service"() {
        def registry = newRegistry()
        def service = Mock(TestCloseService)

        given:
        registry.add(TestCloseService, service)

        when:
        registry.close()

        then:
        1 * service.close()
    }

    def "close invokes stop method on each service"() {
        def registry = newRegistry()
        def service = Mock(TestStopService)

        given:
        registry.add(TestStopService, service)

        when:
        registry.close()

        then:
        1 * service.stop()
    }

    def "close ignores service with no close or stop method"() {
        def registry = newRegistry()
        registry.add(String, "service")
        registry.getAll(String)

        when:
        registry.close()

        then:
        noExceptionThrown()
    }

    def "close invokes close on service created from implementation class"() {
        def registry = newRegistry()

        given:
        registry.register({ registration -> registration.add(CloseableService) })
        def service = registry.get(CloseableService)

        when:
        registry.close()

        then:
        service.closed
    }

    def "close invokes stop on service created by provider factory method"() {
        def registry = newRegistry()
        def service = Mock(TestStopService)

        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestStopService createServices() {
                return service
            }
        })
        registry.get(TestStopService)

        when:
        registry.close()

        then:
        1 * service.stop()
    }

    def "close closes services in dependency order"() {
        def service1 = Mock(TestCloseService)
        def service2 = Mock(TestStopService)
        def service3 = Mock(CloseableService)
        def registry = newRegistry()

        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            CloseableService createService3() {
                return service3
            }

            @Provides
            TestStopService createService2(CloseableService b) {
                return service2
            }

        })
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestCloseService createService1(TestStopService a, CloseableService b) {
                return service1
            }
        })
        registry.get(TestCloseService)

        when:
        registry.close()

        then:
        1 * service1.close()

        then:
        1 * service2.stop()

        then:
        1 * service3.close()
        0 * _._
    }

    def "close closes services in dependency order when services with type are injected"() {
        def service1 = Mock(TestStopService)
        def service2 = Mock(TestCloseService)
        def service3 = Mock(TestCloseService)
        def registry = newRegistry()

        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestCloseService createService3() {
                return service3
            }

            @Provides
            TestCloseService createService2() {
                return service2
            }
        })
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestStopService createService1(List<TestCloseService> services) {
                return service1
            }
        })
        registry.get(TestStopService)

        when:
        registry.close()

        then:
        1 * service1.stop()

        then:
        1 * service2.close()
        1 * service3.close()
        0 * _._
    }

    def "close continues to close services after failing to stop some service"() {
        def service1 = Mock(TestCloseService)
        def service2 = Mock(TestStopService)
        def service3 = Mock(CloseableService)
        def failure = new RuntimeException()
        def registry = newRegistry()

        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestStopService createService2(CloseableService b) {
                return service2
            }

            @Provides
            TestCloseService createService1(TestStopService a) {
                return service1
            }

            @Provides
            CloseableService createService3() {
                return service3
            }
        })
        registry.get(TestCloseService)

        when:
        registry.close()

        then:
        RuntimeException e = thrown()
        e == failure

        and:
        1 * service1.close()
        1 * service2.stop() >> { throw failure }
        1 * service3.close()
        0 * _._
    }

    def "does not stop service that has not been created"() {
        def registry = newRegistry()
        def service = Mock(TestStopService)

        given:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestStopService createServices() {
                return service
            }
        })

        when:
        registry.close()

        then:
        0 * service.stop()
    }

    def "can stop multiple times"() {
        def registry = newRegistry()
        def service = Mock(TestCloseService)

        given:
        registry.add(TestCloseService, service)

        when:
        registry.close()

        then:
        1 * service.close()

        when:
        registry.close()

        then:
        0 * service._
    }

    def "cannot lookup services when closed"() {
        def registry = newRegistry().addProvider(new TestProvider())

        given:
        registry.get(String)
        registry.getAll(String)
        registry.close()

        when:
        registry.get(String)

        then:
        IllegalStateException e = thrown()
        e.message == "test registry has been closed."

        when:
        registry.getAll(String)

        then:
        e = thrown()
        e.message == "test registry has been closed."
    }

    /**
     * Closing children would imply holding a reference to them. This would
     * create memory leaks.
     */
    def "does not close services from child registries"() {
        def registry = newRegistry()

        given:
        def parentService = Mock(TestCloseService)
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            Closeable createCloseableService() {
                parentService
            }
        })

        def child = newRegistry(registry)
        def childService = Mock(TestStopService)
        child.addProvider(new ServiceRegistrationProvider() {
            @Provides
            Stoppable createCloseableService(Closeable dependency) {
                childService
            }
        })

        when:
        def service = child.get(Stoppable)
        registry.close()

        then:
        service == childService
        1 * parentService.close()
        0 * childService.close()
    }

    /**
     * We isolate services in child registries, so we don't leak memory. This test makes
     * sure that we don't overdo the isolation and still track dependencies between services
     * inside a single registry, even when a child requested that service.
     */
    def "closes services in dependency order even when child requested them first"() {
        def service1 = Mock(TestCloseService)
        def service2 = Mock(TestStopService)
        def service3 = Mock(CloseableService)
        def parent = newRegistry()
        def child = newRegistry(parent)

        given:
        parent.addProvider(new ServiceRegistrationProvider() {
            @Provides
            CloseableService createService3() {
                return service3
            }

            @Provides
            TestStopService createService2(CloseableService b) {
                return service2
            }
        })

        child.addProvider(new ServiceRegistrationProvider() {
            @Provides
            TestCloseService createService1(TestStopService a, CloseableService b) {
                return service1
            }
        })
        child.get(TestCloseService)

        when:
        parent.close()

        then:
        1 * service2.stop()

        then:
        1 * service3.close()
    }

    def "cannot add providers after #trigger"() {
        given:
        def registry = newRegistry().addProvider(new TestProvider())
        trigger(registry)

        when:
        registry.addProvider(new ServiceRegistrationProvider() {})

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot add services to service registry test registry as it is no longer mutable"

        where:
        description                   | trigger
        "getting a service via class" | { r -> r.get(Integer) }
        "getting a service via type"  | { r -> r.get(Integer as Type) }
        "getting all services"        | { r -> r.getAll(Integer) }
    }

    def "cannot add instance after #trigger"() {
        given:
        def registry = newRegistry().addProvider(new TestProvider())
        trigger(registry)

        when:
        registry.add(String, "foo")

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot add services to service registry test registry as it is no longer mutable"

        where:
        description                   | trigger
        "getting a service via class" | { r -> r.get(Integer) }
        "getting a service via type"  | { r -> r.get(Integer as Type) }
        "getting all services"        | { r -> r.getAll(Integer) }
    }

    def "cannot lookup services while closing"() {
        def registry = newRegistry()

        given:
        registry.add(Closeable, { registry.get(String) } as Closeable)

        when:
        registry.close()

        then:
        def e = thrown(IllegalStateException)
        e.message == "test registry has been closed."
    }

    private static class TestService {
        static int instancesCreated = 0

        TestService() {
            instancesCreated++
        }
    }

     private static class DependentService {
        TestService dependency

        DependentService(TestService other) {
            dependency = other
        }
    }

    interface TestCloseService extends Closeable {
        void close()
    }

    interface TestStopService extends Stoppable {
        void stop()
    }

    static class CloseableService implements Closeable {
        boolean closed

        void close() {
            closed = true
        }
    }

    private static class TestProvider implements ServiceRegistrationProvider {
        @Provides
        String createString(Integer integer) { integer.toString() }

        @Provides
        Integer createInt() { 12 }
    }
}
