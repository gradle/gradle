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

import org.gradle.internal.Factory
import spock.lang.Specification

import java.util.concurrent.Callable

class ServiceRegistryHierarchyTest extends Specification implements ServiceRegistryFixture {

    def "delegates to single parent for unknown service"() {
        def instance = BigDecimal.TEN
        def service = service(instance)
        def parentProvider = Mock(ServiceProvider)
        def registry = newRegistry(parentRegistry(parentProvider))

        when:
        def result = registry.get(BigDecimal)

        then:
        result.is(instance)

        and:
        1 * parentProvider.getService(BigDecimal, _) >> service
    }

    def "delegates to multiple parents for unknown service"() {
        def instance = BigDecimal.TEN
        def service = service(instance)
        def parentProvider1 = Mock(ServiceProvider)
        def parentProvider2 = Mock(ServiceProvider)
        def registry = newRegistry(parentRegistry(parentProvider1), parentRegistry(parentProvider2))

        when:
        def result = registry.get(BigDecimal)

        then:
        result.is(instance)

        and:
        1 * parentProvider1.getService(BigDecimal, _) >> null
        1 * parentProvider2.getService(BigDecimal, _) >> service
    }

    def "fails when requesting unknown service from parent"() {
        def parentProvider = Mock(ServiceProvider)
        def registry = newRegistry(parentRegistry(parentProvider))

        given:
        _ * parentProvider.getService(StringBuilder, _) >> null

        when:
        registry.get(StringBuilder)

        then:
        UnknownServiceException e = thrown()
        e.message == "No service of type StringBuilder available in test registry."
    }

    def "uses provider decorator method to decorate parent service instance"() {
        def parentRegistry = newRegistry().addProvider(new ServiceRegistrationProvider() {
            @Provides
            Long createLong() { 110L }
        })
        def registry = new DefaultServiceRegistry(parentRegistry)
        registry.addProvider(decoratorProvider)

        expect:
        registry.get(Long) == 112L
        registry.get(Number) == 112L

        where:
        decoratorProvider << [new TestDecoratingProviderWithCreate(), new TestDecoratingProviderWithDecorate()]
    }

    def "injects parent services into provider factory method"() {
        def parentRegistry = newRegistry().addProvider(new ServiceRegistrationProvider() {
            @Provides
            Number createNumber() { 123 }
        })
        def registry = newRegistry(parentRegistry).addProvider(new ServiceRegistrationProvider() {
            @Provides
            String createString(Number n) { n.toString() }
        })

        when:
        def result = registry.get(String)

        then:
        result == '123'
    }

    def "caches service created using provider decorator method"() {
        def parentRegistry = newRegistry().addProvider(new ServiceRegistrationProvider() {
            @Provides
            Long createLong() { 11L }
        })
        def registry = newRegistry(parentRegistry)
        registry.addProvider(decoratorProvider)

        expect:
        registry.get(Long).is(registry.get(Long))

        where:
        decoratorProvider << [new TestDecoratingProviderWithCreate(), new TestDecoratingProviderWithDecorate()]
    }

    def "fails when create and decorate methods decorate the same type"() {
        def parentRegistry = newRegistry().addProvider(new ServiceRegistrationProvider() {
            @Provides
            Long createLong() { 11L }
        })
        def registry = newRegistry(parentRegistry)
        registry.addProvider(new ConflictingDecoratorMethods())

        when:
        registry.get(Long).is(registry.get(Long))

        then:
        ServiceLookupException e = thrown()
        normalizedMessage(e) == """Multiple services of type Long available in test registry:
   - Service Long via ConflictingDecoratorMethods.createLong()
   - Service Long via ConflictingDecoratorMethods.decorateLong()"""
    }

    def "provider decorator method fails when no parent registry"() {
        def registry = new DefaultServiceRegistry()

        when:
        registry.addProvider(decoratorProvider)

        then:
        ServiceLookupException e = thrown()
        e.message == "Cannot use decorator method ${decoratorProvider.class.simpleName}.${methodName}Long() when no parent registry is provided."

        where:
        decoratorProvider                        | methodName
        new TestDecoratingProviderWithCreate()   | 'create'
        new TestDecoratingProviderWithDecorate() | 'decorate'
    }

    def "fails when provider decorator method requires unknown service"() {
        def parentProvider = Mock(ServiceProvider)
        def registry = new DefaultServiceRegistry(parentRegistry(parentProvider))

        given:
        registry.addProvider(decoratorProvider)

        when:
        registry.get(Long)

        then:
        ServiceCreationException e = thrown()
        normalizedMessage(e) ==
            "Cannot create service of type Long using method ${decoratorProvider.class.simpleName}.${methodName}Long()" +
            " as required service of type Long for parameter #1 is not available in parent registries."

        where:
        decoratorProvider                        | methodName
        new TestDecoratingProviderWithCreate()   | 'create'
        new TestDecoratingProviderWithDecorate() | 'decorate'
    }

    def "fails when provider decorator method throws exception"() {
        def parentRegistry = newRegistry().addProvider(new ServiceRegistrationProvider() {
            @Provides
            Long createLong() { 11L }
        })
        def registry = newRegistry(parentRegistry)

        given:
        registry.addProvider(decoratorProvider)

        when:
        registry.get(Long)

        then:
        ServiceCreationException e = thrown()
        e.message == "Could not create service of type Long using ${decoratorProvider.class.simpleName}.${methodName}Long()."
        e.cause == decoratorProvider.failure

        where:
        decoratorProvider                          | methodName
        new BrokenDecoratingProviderWithCreate()   | 'create'
        new BrokenDecoratingProviderWithDecorate() | 'decorate'
    }

    def "uses decorator method to decorate parent service instance"() {
        def parentRegistry = newRegistry().addProvider(new ServiceRegistrationProvider() {
            @Provides
            Long createLong() { 110L }
        })
        def registry = newRegistry(parentRegistry)
        registry.addProvider(provider)

        when:
        def result = registry.get(Long)

        then:
        result == 120L

        where:
        provider << [
            new TestProviderWithDecoratorMethodsWithCreate(),
            new TestProviderWithDecoratorMethodsWithDecorate(),
        ]
    }

    def "decorator methods can take additional parameters"() {
        def parentRegistry = newRegistry().addProvider(new ServiceRegistrationProvider() {
            @Provides
            Long createLong() { 110L }

            @Provides
            String createString() { "Foo" }
        })
        def registry = newRegistry(parentRegistry)
        registry.addProvider(provider)

        when:
        def result = registry.get(String)

        then:
        result == "Foo120"

        where:
        provider << [
            new TestProviderWithDecoratorMethodsWithCreate(),
            new TestProviderWithDecoratorMethodsWithDecorate(),
        ]
    }

    def "decorator create method fails when no parent registry"() {
        def registry = new DefaultServiceRegistry()

        when:
        registry.addProvider(provider)

        then:
        ServiceLookupException e = thrown()
        e.message.matches(/Cannot use decorator method TestProviderWithDecoratorMethodsWith(Create|Decorate)\..*\(\) when no parent registry is provided./)

        where:
        provider << [
            new TestProviderWithDecoratorMethodsWithCreate(),
            new TestProviderWithDecoratorMethodsWithDecorate(),
        ]
    }

    def "injects generic types from parent into provider factory method"() {
        def parent = newRegistry().addProvider(new ServiceRegistrationProvider() {
            @Provides
            Callable<String> createStringCallable() { return { "hello" } }

            @Provides
            Factory<String> createStringFactory() { return { "world" } as Factory }
        })
        def registry = newRegistry(parent).addProvider(new ServiceRegistrationProvider() {
            @Provides
            String createString(Callable<String> callable, Factory<String> factory) { callable.call() + ' ' + factory.create() }
        })

        expect:
        registry.get(String) == 'hello world'
    }

    private AbstractServiceRegistry parentRegistry(ServiceProvider provider) {
        def parent = Mock(AbstractServiceRegistry)
        parent.asServiceProvider() >> provider
        return parent
    }

    private Service service(Object instance) {
        def service = Mock(Service)
        service.get() >> instance
        service
    }


    private static class TestDecoratingProviderWithCreate implements ServiceRegistrationProvider {
        @Provides
        Long createLong(Long value) {
            return value + 2
        }
    }

    private static class TestDecoratingProviderWithDecorate implements ServiceRegistrationProvider {
        @Provides
        Long decorateLong(Long value) {
            return value + 2
        }
    }

    private static class ConflictingDecoratorMethods implements ServiceRegistrationProvider {
        @Provides
        Long createLong(Long value) {
            return value + 2
        }

        @Provides
        Long decorateLong(Long value) {
            return value + 2
        }
    }

    private static class BrokenDecoratingProviderWithCreate implements ServiceRegistrationProvider {
        static def failure = new RuntimeException()

        @Provides
        Long createLong(Long value) {
            throw failure
        }
    }

    private static class BrokenDecoratingProviderWithDecorate implements ServiceRegistrationProvider {
        static def failure = new RuntimeException()

        @Provides
        Long decorateLong(Long value) {
            throw failure
        }
    }

    private static class TestProviderWithDecoratorMethodsWithCreate implements ServiceRegistrationProvider {
        @Provides
        protected Long createLong(Long value) {
            return value + 10
        }

        @Provides
        protected Factory<Long> createLongFactory(final Factory<Long> factory) {
            return new Factory<Long>() {
                Long create() {
                    return factory.create() + 2
                }
            }
        }

        @Provides
        protected String createString(String parentValue, Long myValue) {
            return parentValue + myValue
        }
    }

    private static class TestProviderWithDecoratorMethodsWithDecorate implements ServiceRegistrationProvider {
        @Provides
        protected Long decorateLong(Long value) {
            return value + 10
        }

        @Provides
        protected Factory<Long> decorateLongFactory(final Factory<Long> factory) {
            return new Factory<Long>() {
                Long create() {
                    return factory.create() + 2
                }
            }
        }

        @Provides
        protected String decorateString(String parentValue, Long myValue) {
            return parentValue + myValue
        }
    }

}
