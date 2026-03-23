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

import org.gradle.internal.Factory
import spock.lang.Specification

import java.util.concurrent.Callable

class ServiceRegistryGenericsTest extends Specification {

    def "injects generic types into provider factory method"() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            Integer createInteger(Factory<String> factory) {
                return factory.create().length()
            }

            @Provides
            Factory<String> createString(Callable<String> action) {
                return { action.call() } as Factory
            }

            @Provides
            Callable<String> createAction() {
                return { "hi" }
            }
        })

        expect:
        registry.get(Integer) == 2
    }

    def "handles inheritance in generic types"() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new ProviderWithGenericType())

        expect:
        registry.get(Integer) == 123
    }

    def "can have multiple services with parameterized types and same raw type"() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new ServiceRegistrationProvider() {
            @Provides
            Integer createInteger(Callable<Integer> factory) {
                return factory.call()
            }

            @Provides
            String createString(Callable<String> factory) {
                return factory.call()
            }

            @Provides
            Callable<Integer> createIntFactory() {
                return { 123 }
            }

            @Provides
            Callable<String> createStringFactory() {
                return { "hi" }
            }
        })

        expect:
        registry.get(Integer) == 123
        registry.get(String) == "hi"
    }

    private interface GenericRunnable<T> extends Runnable {}

    private class ProviderWithGenericType implements ServiceRegistrationProvider {
        @Provides
        Integer createInteger(Runnable action) {
            action.run()
            return 123
        }

        @Provides
        GenericRunnable<String> createString() { return () -> {} }
    }

}
