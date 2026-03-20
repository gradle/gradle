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

class ServiceRegistrySelfInjectTest extends Specification implements ServiceRegistryFixture {

    def "injects service registry into provider factory method"() {
        def parentProvider = Mock(ServiceProvider)
        def registry = new DefaultServiceRegistry(parentRegistry(parentProvider))
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            String createString(ServiceRegistry services) {
                assert services.is(registry)
                return services.get(Number).toString()
            }
        })
        registry.add(Integer, 123)

        expect:
        registry.get(String) == '123'
    }

    def "can locate self as a service of type ServiceRegistry"() {
        def registry = new DefaultServiceRegistry()

        expect:
        registry.get(ServiceRegistry) == registry
        registry.find(DefaultServiceRegistry) == null
    }

    def "fails when registering a service of type ServiceRegistry"() {
        def registry = new DefaultServiceRegistry()

        when:
        registry.add(ServiceRegistry, new DefaultServiceRegistry())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Cannot define a service of type ServiceRegistry: Service ServiceRegistry with implementation DefaultServiceRegistry'
    }

    def "fails when provider factory method produces a service of type ServiceRegistry"() {
        def registry = new DefaultServiceRegistry()

        when:
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            ServiceRegistry createServices() {
                return new DefaultServiceRegistry()
            }
        })

        then:
        def e = thrown(IllegalArgumentException)
        normalizedMessage(e) == 'Cannot define a service of type ServiceRegistry: Service ServiceRegistry via <anonymous>.createServices()'
    }

    private AbstractServiceRegistry parentRegistry(ServiceProvider provider) {
        def parent = Mock(AbstractServiceRegistry)
        parent.asServiceProvider() >> provider
        return parent
    }
}
