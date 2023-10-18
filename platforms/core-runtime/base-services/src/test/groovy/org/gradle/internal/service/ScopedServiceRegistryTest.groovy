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
    def "fails when registering a service in a wrong scope"() {
        given:
        def registry = new ScopedServiceRegistry(Scopes.Build)

        when:
        registry.add(new MyService())

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message.contains("Service 'MyService' was declared in scope 'BuildTree' but registered in scope 'Build'")
    }

    def "succeeds when registering a service in the correct scope"() {
        given:
        def registry = new ScopedServiceRegistry(Scopes.BuildTree)
        def service = new MyService()

        when:
        registry.add(service)

        then:
        noExceptionThrown()

        and:
        registry.get(MyService) === service
    }

    @ServiceScope(Scopes.BuildTree)
    class MyService {}
}
