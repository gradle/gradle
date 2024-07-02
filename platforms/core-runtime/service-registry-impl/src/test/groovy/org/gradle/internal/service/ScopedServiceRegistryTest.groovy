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
import org.gradle.internal.service.scopes.ServiceScope
import spock.lang.Specification

class ScopedServiceRegistryTest extends Specification {

    def "fails when registering a service by adding #method in a wrong scope"() {
        given:
        def registry = scopedRegistry(Scope.Build)

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
        def registry = scopedRegistry(Scope.BuildTree)

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
        def registry = scopedRegistry(Scope.BuildTree)
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
        def registry = scopedRegistry(Scope.BuildTree)
        def service = new UnscopedService()

        when:
        registry.add(service)

        then:
        noExceptionThrown()

        and:
        registry.get(UnscopedService) === service
    }

    def "succeeds when registering a service via #method in the correct scope in strict mode"() {
        given:
        def registry = strictScopedRegistry(Scope.BuildTree)

        when:
        registration(registry)
        registry.get(BuildTreeScopedServiceInterface) != null

        then:
        noExceptionThrown()

        where:
        method     | registration
        'instance' | { ScopedServiceRegistry it -> it.add(BuildTreeScopedServiceInterface, new BuildTreeScopedServiceInterfaceUnscopedImpl()) }
        'type'     | { ScopedServiceRegistry it -> it.register { it.add(BuildTreeScopedServiceInterface, BuildTreeScopedServiceInterfaceUnscopedImpl) } }
        'provider' | { ScopedServiceRegistry it -> it.addProvider(new BuildTreeScopedServiceInterfaceProvider()) }
    }

    def "fails when registering an unscoped implementation via #method in strict mode"() {
        given:
        def registry = strictScopedRegistry(Scope.BuildTree)

        when:
        registration(registry)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message.contains("The service implementation '${BuildTreeScopedServiceInterfaceUnscopedImpl.name}' is registered in the 'BuildTree' scope but does not declare it explicitly.")

        where:
        method     | registration
        'instance' | { ScopedServiceRegistry it -> it.add(new BuildTreeScopedServiceInterfaceUnscopedImpl()) }
        'type'     | { ScopedServiceRegistry it -> it.register { it.add(BuildTreeScopedServiceInterfaceUnscopedImpl) } }
        'provider' | { ScopedServiceRegistry it -> it.addProvider(new BuildTreeScopedServiceInterfaceUnscopedImplProvider()) }
    }

    def "fails when registering an unscoped service via #method in strict mode"() {
        given:
        def registry = strictScopedRegistry(Scope.BuildTree)

        when:
        registration(registry)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message.contains("The service '${UnscopedService.name}' is registered in the 'BuildTree' scope but does not declare it. Add the '@ServiceScope()' annotation on '${UnscopedService.simpleName}' with the 'BuildTree' scope.")

        where:
        method     | registration
        'instance' | { ScopedServiceRegistry it -> it.add(new UnscopedService()) }
        'type'     | { ScopedServiceRegistry it -> it.register { it.add(UnscopedService) } }
        'provider' | { ScopedServiceRegistry it -> it.addProvider(new UnscopedServiceProvider()) }
    }

    def "succeeds when registering a multi-scoped service in the correct scope (#scopeName)"() {
        given:
        def registry = scopedRegistry(scope)
        def service = new GlobalAndBuildScopedService()

        when:
        registry.add(service)

        then:
        noExceptionThrown()

        and:
        registry.get(GlobalAndBuildScopedService) === service

        where:
        scope << [Scope.Global, Scope.Build]
        scopeName = scope.simpleName
    }

    private static ScopedServiceRegistry scopedRegistry(Class<? extends Scope> scope) {
        return scopedRegistry(scope, false)
    }

    private static ScopedServiceRegistry strictScopedRegistry(Class<? extends Scope> scope) {
        return scopedRegistry(scope, true)
    }

    private static ScopedServiceRegistry scopedRegistry(Class<? extends Scope> scope, boolean strict) {
        return new ScopedServiceRegistry(scope, strict, "test service registry")
    }

    @ServiceScope(Scope.BuildTree)
    static class BuildTreeScopedService {}

    @ServiceScope([Scope.Global, Scope.Build])
    static class GlobalAndBuildScopedService {}

    static class UnscopedService {}

    static class UnscopedServiceProvider implements ServiceRegistrationProvider {
        @Provides
        UnscopedService createScopedService() {
            return new UnscopedService()
        }
    }

    // Important that this is an interface, because then `@ServiceScope` is not inherited for implementations
    @ServiceScope(Scope.BuildTree)
    interface BuildTreeScopedServiceInterface {}

    static class BuildTreeScopedServiceInterfaceUnscopedImpl implements BuildTreeScopedServiceInterface {}

    static class BuildTreeScopedServiceProvider implements ServiceRegistrationProvider {
        @Provides
        BuildTreeScopedService createScopedService() {
            return new BuildTreeScopedService()
        }
    }

    static class BuildTreeScopedServiceInterfaceProvider implements ServiceRegistrationProvider {
        @Provides
        BuildTreeScopedServiceInterface createScopedService() {
            return new BuildTreeScopedServiceInterfaceUnscopedImpl()
        }
    }

    static class BuildTreeScopedServiceInterfaceUnscopedImplProvider implements ServiceRegistrationProvider {
        @Provides
        BuildTreeScopedServiceInterfaceUnscopedImpl createScopedService() {
            return new BuildTreeScopedServiceInterfaceUnscopedImpl()
        }
    }

    static class MultiScopedServiceProvider implements ServiceRegistrationProvider {
        @Provides
        GlobalAndBuildScopedService createScopedService() {
            return new GlobalAndBuildScopedService()
        }
    }

    static class BrokenScopedServiceRegistry extends ScopedServiceRegistry {
        BrokenScopedServiceRegistry() {
            super(Scope.Build, "broken service registry")
        }

        @Provides
        BuildTreeScopedService createScopedService() {
            return new BuildTreeScopedService()
        }
    }
}
