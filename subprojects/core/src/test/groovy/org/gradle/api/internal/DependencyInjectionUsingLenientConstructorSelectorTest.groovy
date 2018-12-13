/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.internal.instantiation.ParamsMatchingConstructorSelector
import org.gradle.api.reflect.ObjectInstantiationException
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.service.ServiceRegistry
import org.gradle.testing.internal.util.Specification

class DependencyInjectionUsingLenientConstructorSelectorTest extends Specification {
    def services = Mock(ServiceRegistry)
    def classGenerator = new IdentityClassGenerator()
    def instantiator = new DependencyInjectingInstantiator(new ParamsMatchingConstructorSelector(classGenerator, new TestCrossBuildInMemoryCacheFactory.TestCache()), classGenerator, services)

    def "creates instance that has default constructor"() {
        when:
        def result = instantiator.newInstance(HasDefaultConstructor)

        then:
        result instanceof HasDefaultConstructor
    }

    def "injects provided parameters into constructor"() {
        when:
        def result = instantiator.newInstance(HasConstructor, "string", 12)

        then:
        result.param1 == "string"
        result.param2 == 12
    }

    def "injects missing parameters from provided service registry"() {
        given:
        services.find(String) >> "string"

        when:
        def result = instantiator.newInstance(HasConstructor, 12)

        then:
        result.param1 == "string"
        result.param2 == 12
    }

    def "unboxes primitive types"() {
        when:
        def result = instantiator.newInstance(AcceptsPrimitiveTypes, 12, true)

        then:
        result.param1 == 12
        result.param2
    }

    def "allows null parameters"() {
        when:
        def result = instantiator.newInstance(HasConstructor, null, null)

        then:
        result.param1 == null
        result.param2 == null
    }

    def "does not allows null parameters when services expected"() {
        when:
        instantiator.newInstance(HasConstructor, [null] as Object[])

        then:
        ObjectInstantiationException e = thrown()
        e.cause instanceof IllegalArgumentException
        e.cause.message == "Unable to determine $HasConstructor.name argument #1: value null not assignable to type class java.lang.String, or no service of type class java.lang.String"
    }

    static class HasDefaultConstructor {
    }

    static class HasConstructor {
        String param1
        Number param2

        HasConstructor(String param1, Number param2) {
            this.param1 = param1
            this.param2 = param2
        }
    }

    static class AcceptsPrimitiveTypes {
        int param1
        boolean param2

        AcceptsPrimitiveTypes(int param1, boolean param2) {
            this.param1 = param1
            this.param2 = param2
        }
    }
}
