/*
 * Copyright 2023 the original author or authors.
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


import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import spock.lang.Specification

class ScopedServiceRegistryTest extends Specification {

    def "fails when registering a service by adding #method in a wrong scope"() {
        given:
        def registry = new ScopedServiceRegistry(Scopes.Build)

        when:
        registration(registry)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message.contains("Service '${BuildTreeScopedService.name}' was declared in scope 'BuildTree' but registered in scope 'Build'")

        where:
        method     | registration
        'instance' | { ScopedServiceRegistry it -> it.add(new BuildTreeScopedService()) }
        'type'     | { ScopedServiceRegistry it -> it.register { it.add(BuildTreeScopedService) } }
        'provider' | { ScopedServiceRegistry it -> it.addProvider(new ScopedServiceProvider()) }
    }

    def "succeeds when registering a service in the correct scope"() {
        given:
        def registry = new ScopedServiceRegistry(Scopes.BuildTree)
        def service = new BuildTreeScopedService()

        when:
        registry.add(service)

        then:
        noExceptionThrown()

        and:
        registry.get(BuildTreeScopedService) === service
    }

    def "fails to create an inherited registry providing a service in the wrong scope"() {
        when:
        new BrokenScopedServiceRegistry()

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message.contains("Service '${BuildTreeScopedService.name}' was declared in scope 'BuildTree' but registered in scope 'Build'")
    }

    def "succeeds when registering an unscoped service"() {
        given:
        def registry = new ScopedServiceRegistry(Scopes.BuildTree)
        def service = new UnscopedService()

        when:
        registry.add(service)

        then:
        noExceptionThrown()

        and:
        registry.get(UnscopedService) === service
    }

    @ServiceScope(Scopes.BuildTree)
    static class BuildTreeScopedService {}

    static class UnscopedService {}

    static class ScopedServiceProvider {
        @SuppressWarnings('unused')
        BuildTreeScopedService createScopedService() {
            return new BuildTreeScopedService()
        }
    }

    static class BrokenScopedServiceRegistry extends ScopedServiceRegistry {
        BrokenScopedServiceRegistry() {
            super(Scopes.Build)
        }

        @SuppressWarnings('unused')
        BuildTreeScopedService createScopedService() {
            return new BuildTreeScopedService()
        }
    }
}
