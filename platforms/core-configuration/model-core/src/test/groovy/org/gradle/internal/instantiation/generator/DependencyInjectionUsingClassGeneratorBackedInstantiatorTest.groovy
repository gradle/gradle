/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.internal.instantiation.generator

import org.gradle.api.tasks.Nested
import org.gradle.cache.internal.CrossBuildInMemoryCache
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.instantiation.PropertyRoleAnnotationHandler
import org.gradle.internal.instantiation.managed.DefaultManagedObjectRegistry
import org.gradle.internal.instantiation.managed.ManagedObjectRegistry
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceLookup
import org.gradle.util.TestUtil
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Specification

import javax.inject.Inject

class DependencyInjectionUsingClassGeneratorBackedInstantiatorTest extends Specification {
    final ClassGenerator classGenerator = AsmBackedClassGenerator.decorateAndInject([], Stub(PropertyRoleAnnotationHandler), [], new TestCrossBuildInMemoryCacheFactory(), 0)
    final CrossBuildInMemoryCache cache = new TestCrossBuildInMemoryCacheFactory().newCache()
    final ServiceLookup services = new DefaultServiceRegistry()
    final DependencyInjectingInstantiator instantiator = new DependencyInjectingInstantiator(new Jsr330ConstructorSelector(classGenerator, cache), services)

    def setup() {
        services.add(InstantiatorFactory, TestUtil.instantiatorFactory())
    }

    def "injects service using getter injection"() {
        given:
        services.add(String, "string")

        when:
        def result = instantiator.newInstance(HasGetterInjection)

        then:
        result.someService == 'string'
    }

    def "injects service using abstract getter injection"() {
        given:
        services.add(String, "string")

        when:
        def result = instantiator.newInstance(AbstractHasGetterInjection)

        then:
        result.someService == 'string'
    }

    def "constructor can use getter injected service"() {
        given:
        services.add(String, "string")

        when:
        def result = instantiator.newInstance(UsesInjectedServiceFromConstructor)

        then:
        result.result == 'string'
        result.someService == 'string'
    }

    def "constructor can receive injected service and parameter"() {
        given:
        services.add(String, "string")

        when:
        def result = instantiator.newInstance(HasInjectConstructor, 12)

        then:
        result.param1 == "string"
        result.param2 == 12
    }

    def "can use factory to create instance with injected service and parameter"() {
        given:
        services.add(String, "string")

        when:
        def factory = instantiator.factoryFor(HasInjectConstructor)
        def result = factory.newInstance(services, 12)

        then:
        result.param1 == "string"
        result.param2 == 12
    }

    def "can use factory to create instance with injected service using getter"() {
        given:
        services.add(String, "string")

        when:
        def factory = instantiator.factoryFor(HasGetterInjection)
        def result = factory.newInstance(services)

        then:
        result.someService == "string"
    }

    def "can query whether service is required when declared as constructor parameter"() {
        when:
        def factory = instantiator.factoryFor(HasInjectConstructor)

        then:
        factory.requiresService(String)
        factory.requiresService(Number)
        !factory.requiresService(Runnable)
    }

    def "can query whether service is required when declared as getter"() {
        when:
        def factory = instantiator.factoryFor(HasGetterInjection)

        then:
        factory.requiresService(String)
        !factory.requiresService(Number)
        !factory.requiresService(Runnable)
    }

    @ToBeImplemented("requiresService only inspects the type itself, not its nested managed types")
    def "can query whether service is required when declared in a nested managed type with #type.simpleName"() {
        given:
        services.add(String, "string")
        services.add(ManagedObjectRegistry, new DefaultManagedObjectRegistry())

        when:
        def factory = instantiator.factoryFor(type)

        then:
        factory.newInstance(services).nested.someService == "string"
        !factory.requiresService(String) // should be required, since the nested instance receives it
        !factory.requiresService(Runnable)

        where:
        type << [NestsGetterInjection, NestsInjectConstructor]
    }

    static class HasGetterInjection {
        @Inject String getSomeService() { throw new UnsupportedOperationException() }
    }

    static abstract class AbstractHasGetterInjection {
        abstract @Inject String getSomeService()
    }

    static class UsesInjectedServiceFromConstructor {
        final String result

        UsesInjectedServiceFromConstructor() {
            result = someService
        }

        @Inject
        String getSomeService() { throw new UnsupportedOperationException() }
    }

    static class HasInjectConstructor {
        String param1
        Number param2

        @Inject
        HasInjectConstructor(String param1, Number param2) {
            this.param1 = param1
            this.param2 = param2
        }
    }

    static class HasSingleInjectConstructor {
        final String someService

        @Inject
        HasSingleInjectConstructor(String someService) {
            this.someService = someService
        }
    }

    static abstract class NestsGetterInjection {
        @Nested
        abstract AbstractHasGetterInjection getNested()
    }

    static abstract class NestsInjectConstructor {
        @Nested
        abstract HasSingleInjectConstructor getNested()
    }
}
