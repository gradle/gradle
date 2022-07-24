/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.provider

import org.gradle.api.provider.Provider
import org.gradle.internal.state.ManagedFactory

import java.util.concurrent.Callable

class DefaultProviderTest extends ProviderSpec<String> {
    @Override
    DefaultProvider<String> providerWithNoValue() {
        return new DefaultProvider<String>({ null })
    }

    @Override
    DefaultProvider<String> providerWithValue(String value) {
        return new DefaultProvider<String>({ value })
    }

    @Override
    Class<String> type() {
        return String
    }

    @Override
    String someValue() {
        return "s1"
    }

    @Override
    String someOtherValue() {
        return "other1"
    }

    @Override
    String someOtherValue2() {
        return "other2"
    }

    @Override
    String someOtherValue3() {
        return "other3"
    }

    @Override
    ManagedFactory managedFactory() {
        return new ManagedFactories.ProviderManagedFactory()
    }

    def "toString() does not realize value"() {
        given:
        def providerWithBadValue = new DefaultProvider<String>({
            throw new RuntimeException()
        })

        expect:
        providerWithBadValue.toString() == "provider(?)"
        Providers.notDefined().toString() == "undefined"
    }

    def "rethrows exception if value calculation throws exception"() {
        def failure = new RuntimeException('Something went wrong')

        given:
        def provider = new DefaultProvider({ throw failure })

        when:
        provider.get()

        then:
        def t = thrown(RuntimeException)
        t == failure
    }

    def "infers value type from callable implementation"() {
        def provider = new DefaultProvider(callable)

        expect:
        provider.type == valueType

        where:
        callable                    | valueType
        new RawCallable()           | null
        new StringCallable()        | String
        new ParameterizedCallable() | null
        ({ 123 } as Callable)       | null
    }

    static Provider createProvider() {
        new DefaultProvider({})
    }

    static Provider createProvider(value) {
        new DefaultProvider({ value })
    }

    static class RawCallable implements Callable {
        @Override
        Object call() throws Exception {
            return null
        }
    }

    static class StringCallable implements Callable<String> {
        @Override
        String call() throws Exception {
            return null
        }
    }

    static class ParameterizedCallable<T> implements Callable<List<T>> {
        @Override
        List<T> call() throws Exception {
            return null
        }
    }
}
