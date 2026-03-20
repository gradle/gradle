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

class ServiceRegistryMultiServiceTest extends Specification implements ServiceRegistryFixture {

    def "can lookup a multi-service by any service type declared via @Provides"() {
        given:
        def registry = newRegistry()
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides([TestService, AnotherTestService])
            TestMultiServiceImpl create() { new TestMultiServiceImpl() }
        })

        when:
        def service1 = registry.get(TestService)
        then:
        (service1 instanceof TestService)

        when:
        def service2 = registry.get(AnotherTestService)
        then:
        (service2 instanceof AnotherTestService)

        when:
        registry.get(TestMultiServiceImpl)
        then:
        def e = thrown(UnknownServiceException)
        normalizedMessage(e) == "No service of type TestMultiServiceImpl available in test registry."
    }

    def "can lookup a multi-service by any declared service type added via registration"() {
        given:
        def registry = newRegistry()
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            void configure(ServiceRegistration registration) {
                registration.add(TestService, AnotherTestService, TestMultiServiceImpl)
            }
        })

        when:
        def service1 = registry.get(TestService)
        then:
        (service1 instanceof TestService)

        when:
        def service2 = registry.get(AnotherTestService)
        then:
        service2 instanceof AnotherTestService
    }

    def "cannot lookup implementation of a multi-service with declared service types added via registration"() {
        given:
        def registry = newRegistry()
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            void configure(ServiceRegistration registration) {
                registration.add(TestService, AnotherTestService, TestMultiServiceImpl)
            }
        })

        when:
        registry.get(TestMultiServiceImpl)
        then:
        def e = thrown(UnknownServiceException)
        normalizedMessage(e) == "No service of type TestMultiServiceImpl available in test registry."
    }

    private interface TestService {
    }

    private interface AnotherTestService {
    }

    private static class TestMultiServiceImpl implements TestService, AnotherTestService {
    }
}
