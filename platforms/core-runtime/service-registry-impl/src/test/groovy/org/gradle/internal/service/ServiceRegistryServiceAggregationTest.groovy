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

import com.google.common.reflect.TypeToken
import org.gradle.internal.Factory
import spock.lang.Specification

class ServiceRegistryServiceAggregationTest extends Specification implements ServiceRegistryFixture {

    def "can get all services of a given type"() {
        def registry = newRegistry().addProvider(new TestProvider())
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            String createOtherString() {
                return "hi"
            }
        })

        expect:
        registry.getAll(String) == ["12", "hi"]
        registry.getAll(Number) == [12]
    }

    def "removes duplicate services when parent is reachable via multiple paths"() {
        def root = new DefaultServiceRegistry()
        root.add(String, "root")
        def parent1 = new DefaultServiceRegistry(root)
        parent1.add(String, "p1")
        def parent2 = new DefaultServiceRegistry(root)
        parent2.add(String, "p2")
        def registry = new DefaultServiceRegistry(parent1, parent2)

        expect:
        registry.getAll(String) == ["p1", "root", "p2"]
    }

    def "can get all services of a given type using collection type"() {
        def registry = newRegistry().addProvider(new TestProvider())
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            String createOtherString() {
                return "hi"
            }
        })

        expect:
        registry.get(new TypeToken<List<String>>() {}.type) == ["12", "hi"]
        registry.get(new TypeToken<List<Number>>() {}.type) == [12]
        registry.get(new TypeToken<List<? extends CharSequence>>() {}.type) == ["12", "hi"]
        registry.get(new TypeToken<List<? extends Number>>() {}.type) == [12]
    }

    def "can get all services of a raw type"() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            String createString() {
                return "hi"
            }

            @Provides
            Factory<String> createFactory() {
                return {} as Factory
            }

            @Provides
            CharSequence createCharSequence() {
                return "foo"
            }
        })

        expect:
        registry.getAll(Factory).size() == 1
        registry.getAll(CharSequence).size() == 2
    }

    def "all services returns empty collection when no services of given type"() {
        def registry = newRegistry().addProvider(new TestProvider())

        expect:
        registry.getAll(Long).empty
    }

    def "all services includes services from parents"() {
        def parentProvider1 = Stub(ServiceProvider) {
            getAll(Number, _, _) >> { Class type, token, ServiceProvider.Visitor visitor ->
                visitor.visit(new ServiceWrapper(123L))
                return visitor
            }
        }
        def parentProvider2 = Stub(ServiceProvider) {
            getAll(Number, _, _) >> { Class type, token, ServiceProvider.Visitor visitor ->
                visitor.visit(new ServiceWrapper(456))
                return visitor
            }
        }
        def registry = new DefaultServiceRegistry(parentRegistry(parentProvider1), parentRegistry(parentProvider2))
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            Long createLong() {
                return 12
            }
        })

        expect:
        registry.getAll(Number) == [12, 123L, 456]
    }

    def "all services does not include decorated services from parents"() {
        def longService = new ServiceWrapper(123L)
        def parentProvider1 = Stub(ServiceProvider) {
            getService(Long, _) >> longService
            getAll(Number, _, _) >> { Class type, token, ServiceProvider.Visitor visitor ->
                visitor.visit(longService)
                return visitor
            }
        }
        def parentProvider2 = Stub(ServiceProvider) {
            getAll(Number, _, _) >> { Class type, token, ServiceProvider.Visitor visitor ->
                visitor.visit(new ServiceWrapper(456))
                return visitor
            }
        }
        def registry = new DefaultServiceRegistry(parentRegistry(parentProvider1), parentRegistry(parentProvider2))
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            Long createLong(Long parent) {
                return parent + 1
            }
        })

        expect:
        registry.getAll(Number) == [124L, 456]
    }

    def "injects all services of a given type into service implementation"() {
        def parent = new DefaultServiceRegistry()
        parent.register { ServiceRegistration registration ->
            registration.add(TestServiceImpl)
        }
        def registry = new DefaultServiceRegistry(parent)
        registry.register { ServiceRegistration registration ->
            registration.add(ServiceWithMultipleDependencies)
            registration.add(TestServiceImpl)
        }

        expect:
        registry.get(ServiceWithMultipleDependencies).services.size() == 2
        registry.get(ServiceWithMultipleDependencies).services == registry.getAll(TestServiceImpl)
        registry.get(ServiceWithMultipleDependencies).services == [registry.get(TestService), parent.get(TestService)]
    }

    def "removes duplicate injected services of a given type when parent is reachable from multiple paths"() {
        def root = new DefaultServiceRegistry()
        root.add(TestServiceImpl, new TestServiceImpl())
        def parent1 = new DefaultServiceRegistry(root)
        parent1.register { ServiceRegistration registration ->
            registration.add(TestServiceImpl)
        }
        def parent2 = new DefaultServiceRegistry(root)
        parent2.register { ServiceRegistration registration ->
            registration.add(TestServiceImpl)
        }
        def registry = new DefaultServiceRegistry(parent1, parent2)
        registry.register { ServiceRegistration registration ->
            registration.add(ServiceWithMultipleDependencies)
        }

        expect:
        registry.get(ServiceWithMultipleDependencies).services.size() == 3
        registry.get(ServiceWithMultipleDependencies).services == [parent1.get(TestService), root.get(TestService), parent2.get(TestService)]
    }

    def "injects empty list when no services of given type"() {
        def parent = new DefaultServiceRegistry()
        def registry = new DefaultServiceRegistry(parent)
        registry.register { ServiceRegistration registration ->
            registration.add(ServiceWithMultipleDependencies)
        }

        expect:
        registry.get(ServiceWithMultipleDependencies).services.empty
    }

    def "can use wildcards to inject all services with type"() {
        def parent = new DefaultServiceRegistry()
        parent.register { ServiceRegistration registration ->
            registration.add(TestServiceImpl)
        }
        def registry = new DefaultServiceRegistry(parent)
        registry.register { ServiceRegistration registration ->
            registration.add(ServiceWithWildCardDependencies)
            registration.add(TestServiceImpl)
        }

        expect:
        registry.get(ServiceWithWildCardDependencies).services.size() == 2
        registry.get(ServiceWithWildCardDependencies).services == registry.getAll(TestService)
    }

    def "cannot use lower bound wildcard to inject all services with type"() {
        def registry = new DefaultServiceRegistry()

        given:
        registry.addProvider(new UnsupportedWildcardProvider())

        when:
        registry.get(Number)

        then:
        def e = thrown(ServiceCreationException)
        normalizedMessage(e) == 'Cannot create service of type Number using method UnsupportedWildcardProvider.create() as there is a problem with parameter #1 of type List<? super java.lang.String>.'
        e.cause.message == 'Locating services with type ? super java.lang.String is not supported.'
    }

    private AbstractServiceRegistry parentRegistry(ServiceProvider provider) {
        def parent = Mock(AbstractServiceRegistry)
        parent.asServiceProvider() >> provider
        return parent
    }

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

    private static class ServiceWithMultipleDependencies {
        final List<TestService> services

        ServiceWithMultipleDependencies(List<TestService> services) {
            this.services = services
        }
    }

    private static class ServiceWithWildCardDependencies {
        final List<?> services

        ServiceWithWildCardDependencies(List<? extends TestService> services) {
            this.services = services
        }
    }

    private static class UnsupportedWildcardProvider implements ServiceRegistrationProvider {
        @Provides
        Number create(List<? super String> values) {
            return values.length
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
    }
}
