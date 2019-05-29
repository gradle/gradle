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

package org.gradle.internal.instantiation

import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceLookup
import spock.lang.Specification

import javax.inject.Inject
import java.lang.reflect.Type


class DefaultInstantiationSchemeTest extends Specification {
    def scheme = new DefaultInstantiationScheme(new Jsr330ConstructorSelector(new IdentityClassGenerator(), new TestCrossBuildInMemoryCacheFactory.TestCache<Class<?>, Jsr330ConstructorSelector.CachedConstructor>()), new DefaultServiceRegistry(), [] as Set)

    def "can specify a set of services to inject"() {
        def services = Mock(ServiceLookup)
        _ * services.find((Type) String) >> "value"

        when:
        def value = scheme.withServices(services).instantiator().newInstance(WithServices)

        then:
        value.prop == "value"
    }

    def "can create instances without invoking their constructor"() {
        when:
        def value = scheme.deserializationInstantiator().newInstance(Impl, Base)

        then:
        value.prop == "default"
    }

    static class Base {
        String prop

        Base() {
            prop = "default"
        }
    }

    static class Impl extends Base {
        Impl() {
            throw new RuntimeException("should not be called")
        }
    }

    static class WithServices {
        String prop

        @Inject
        WithServices(String value) {
            prop = value
        }
    }
}
