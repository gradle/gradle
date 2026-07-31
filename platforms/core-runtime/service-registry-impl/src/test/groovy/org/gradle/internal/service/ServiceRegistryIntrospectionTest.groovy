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

import spock.lang.Specification

class ServiceRegistryIntrospectionTest extends Specification implements ServiceRegistryFixture {

    interface ParentService {}

    static class ParentServiceImpl implements ParentService {}

    interface ChildService {}

    static class ChildServiceImpl implements ChildService {}

    def "enumerates service types from this registry and all ancestors, including their interfaces"() {
        given:
        def parent = newRegistry()
        parent.add(ParentServiceImpl, new ParentServiceImpl())
        def registry = newRegistry(parent)
        registry.add(ChildServiceImpl, new ChildServiceImpl())

        when:
        def types = registry.allServiceTypes

        then:
        // own service: declared type and the interface it implements
        types.contains(ChildServiceImpl)
        types.contains(ChildService)

        and:
        // ancestor service is reachable too
        types.contains(ParentServiceImpl)
        types.contains(ParentService)

        and:
        // the registry always provides itself as a service
        types.contains(ServiceRegistry)

        and:
        // the shared hierarchy root is not reported as a service type
        !types.contains(Object)
    }

    def "enumeration does not realize any service"() {
        given:
        def realized = false
        def registry = newRegistry()
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            ChildService createChild() {
                realized = true
                return new ChildServiceImpl()
            }
        })

        when:
        def types = registry.allServiceTypes

        then:
        types.contains(ChildService)
        !realized
    }
}
