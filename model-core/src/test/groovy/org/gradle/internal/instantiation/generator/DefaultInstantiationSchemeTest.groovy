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

import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.instantiation.PropertyRoleAnnotationHandler
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceLookup
import spock.lang.Specification

import javax.inject.Inject

class DefaultInstantiationSchemeTest extends Specification {
    def cacheFactory = new TestCrossBuildInMemoryCacheFactory()
    def classGenerator = AsmBackedClassGenerator.injectOnly([], Stub(PropertyRoleAnnotationHandler), [], cacheFactory, 123)
    def scheme = new DefaultInstantiationScheme(
        new Jsr330ConstructorSelector(classGenerator, cacheFactory.newClassCache()),
        classGenerator,
        new DefaultServiceRegistry(),
        [] as Set,
        new TestCrossBuildInMemoryCacheFactory())

    def "can specify a set of services to inject"() {
        def services = Mock(ServiceLookup)
        _ * services.get(String) >> "value"

        when:
        def value = scheme.withServices(services).instantiator().newInstance(WithServices)

        then:
        value.prop == "value"
    }

    def "can create instances without invoking their constructor to use for deserialization"() {
        when:
        def value = scheme.deserializationInstantiator().newInstance(Impl, Base)

        then:
        value.prop == "default"
    }

    def "can inject services into instances created for deserialization"() {
        def services = Mock(ServiceLookup)
        _ * services.get(String) >> "value"

        when:
        def value = scheme.withServices(services).deserializationInstantiator().newInstance(WithServices, Object)

        then:
        value.prop == "value"
    }

    static class Base {
        String prop

        Base() {
            prop = "default"
        }
    }

    static abstract class Impl extends Base {
        Impl() {
            throw new RuntimeException("should not be called")
        }
    }

    static abstract class WithServices {
        @Inject
        abstract String getProp()
    }
}
