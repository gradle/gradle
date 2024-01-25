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

import org.gradle.internal.service.scopes.Scope
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
        exception.message.contains("The service '${BuildTreeScopedService.name}' declares service scope 'BuildTree' but is registered in the 'Build' scope. Either update the '@ServiceScope()' annotation on '${BuildTreeScopedService.simpleName}' to include the 'Build' scope or move the service registration to one of the declared scopes.")

        where:
        method     | registration
        'instance' | { ScopedServiceRegistry it -> it.add(new BuildTreeScopedService()) }
        'type'     | { ScopedServiceRegistry it -> it.register { it.add(BuildTreeScopedService) } }
        'provider' | { ScopedServiceRegistry it -> it.addProvider(new BuildTreeScopedServiceProvider()) }
    }

    def "fails when registering a multi-scoped service by adding #method in a wrong scope"() {
        given:
        def registry = new ScopedServiceRegistry(Scopes.BuildTree)

        when:
        registration(registry)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message.contains("The service '${GlobalAndBuildScopedService.name}' declares service scopes 'Global', 'Build' but is registered in the 'BuildTree' scope. Either update the '@ServiceScope()' annotation on '${GlobalAndBuildScopedService.simpleName}' to include the 'BuildTree' scope or move the service registration to one of the declared scopes.")

        where:
        method     | registration
        'instance' | { ScopedServiceRegistry it -> it.add(new GlobalAndBuildScopedService()) }
        'type'     | { ScopedServiceRegistry it -> it.register { it.add(GlobalAndBuildScopedService) } }
        'provider' | { ScopedServiceRegistry it -> it.addProvider(new MultiScopedServiceProvider()) }
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
        exception.message.contains("The service '${BuildTreeScopedService.name}' declares service scope 'BuildTree' but is registered in the 'Build' scope. Either update the '@ServiceScope()' annotation on '${BuildTreeScopedService.simpleName}' to include the 'Build' scope or move the service registration to one of the declared scopes.")
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

    def "succeeds when registering a multi-scoped service in the correct scope (#scopeName)"() {
        given:
        def registry = new ScopedServiceRegistry(scope)
        def service = new GlobalAndBuildScopedService()

        when:
        registry.add(service)

        then:
        noExceptionThrown()

        and:
        registry.get(GlobalAndBuildScopedService) === service

        where:
        scope << [Scope.Global, Scopes.Build]
        scopeName = scope.simpleName
    }

    @ServiceScope(Scopes.BuildTree)
    static class BuildTreeScopedService {}

    @ServiceScope([Scope.Global, Scopes.Build])
    static class GlobalAndBuildScopedService {}

    static class UnscopedService {}

    static class BuildTreeScopedServiceProvider {
        @SuppressWarnings('unused')
        BuildTreeScopedService createScopedService() {
            return new BuildTreeScopedService()
        }
    }

    static class MultiScopedServiceProvider {
        @SuppressWarnings('unused')
        GlobalAndBuildScopedService createScopedService() {
            return new GlobalAndBuildScopedService()
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
