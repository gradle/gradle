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

class ServiceRegistryBuilderTest extends Specification {

    def "creates a scope-validating service registry when setting a scope"() {
        def scopedBuilder = ServiceRegistryBuilder.builder()
            .scope(Scopes.Build)

        when:
        scopedBuilder
            .provider(new ScopedServiceProvider())
            .build()

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message.contains("Service '${BuildTreeScopedService.name}' was declared in scope 'BuildTree' but registered in scope 'Build'")
    }

    @ServiceScope(Scopes.BuildTree)
    static class BuildTreeScopedService {}

    static class ScopedServiceProvider {
        @SuppressWarnings('unused')
        BuildTreeScopedService createScopedService() {
            return new BuildTreeScopedService()
        }
    }

}
