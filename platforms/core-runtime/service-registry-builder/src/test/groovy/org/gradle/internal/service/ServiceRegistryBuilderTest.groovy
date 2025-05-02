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

class ServiceRegistryBuilderTest extends Specification {

    def "creates a scope-validating service registry when setting a scope"() {
        def scopedBuilder = ServiceRegistryBuilder.builder()
            .scope(Scope.Build)

        when:
        scopedBuilder
            .provider(new ScopedServiceProvider())
            .build()

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message.contains("The service '${BuildTreeScopedService.name}' declares service scope 'BuildTree' but is registered in the 'Build' scope. Either update the '@ServiceScope()' annotation on '${BuildTreeScopedService.simpleName}' to include the 'Build' scope or move the service registration to one of the declared scopes.")
    }

    @ServiceScope(Scope.BuildTree)
    static class BuildTreeScopedService {}

    static class ScopedServiceProvider implements ServiceRegistrationProvider {
        @Provides
        BuildTreeScopedService createScopedService() {
            return new BuildTreeScopedService()
        }
    }

}
