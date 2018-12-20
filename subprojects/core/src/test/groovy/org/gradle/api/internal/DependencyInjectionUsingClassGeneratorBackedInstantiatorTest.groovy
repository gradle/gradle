/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal

import org.gradle.api.internal.instantiation.Jsr330ConstructorSelector
import org.gradle.cache.internal.CrossBuildInMemoryCache
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.service.ServiceRegistry
import spock.lang.Specification

import javax.inject.Inject

class DependencyInjectionUsingClassGeneratorBackedInstantiatorTest extends Specification {
    final ClassGenerator classGenerator = AsmBackedClassGenerator.decorateAndInject()
    final CrossBuildInMemoryCache cache = new TestCrossBuildInMemoryCacheFactory().newCache()
    final ServiceRegistry services = Mock()
    final DependencyInjectingInstantiator instantiator = new DependencyInjectingInstantiator(new Jsr330ConstructorSelector(classGenerator, cache), classGenerator, services)

    def "injects service using getter injection"() {
        given:
        _ * services.get(String) >> "string"

        when:
        def result = instantiator.newInstance(HasGetterInjection)

        then:
        result.someService == 'string'
    }

    def "injects service using abstract getter injection"() {
        given:
        _ * services.get(String) >> "string"

        when:
        def result = instantiator.newInstance(AbstractHasGetterInjection)

        then:
        result.someService == 'string'
    }

    def "constructor can use getter injected service"() {
        given:
        _ * services.get(String) >> "string"

        when:
        def result = instantiator.newInstance(UsesInjectedServiceFromConstructor)

        then:
        result.result == 'string'
        result.someService == 'string'
    }

    def "constructor can receive injected service and parameter"() {
        given:
        _ * services.find(String) >> "string"

        when:
        def result = instantiator.newInstance(HasInjectConstructor, 12)

        then:
        result.param1 == "string"
        result.param2 == 12
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
}
