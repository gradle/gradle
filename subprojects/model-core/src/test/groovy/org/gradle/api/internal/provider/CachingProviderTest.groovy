/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.internal.state.ManagedFactory

import java.util.concurrent.Callable

import static org.gradle.api.internal.provider.DefaultProviderTest.*

class CachingProviderTest extends ProviderSpec<String> {
    @Override
    CachingProvider<String> providerWithNoValue() {
        return new CachingProvider<String>({ null })
    }

    @Override
    CachingProvider<String> providerWithValue(String value) {
        return new CachingProvider<String>({ value })
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

    def "value is lazily calculated only once"() {
        given:
        def calcCount = 0

        when:
        def provider = new CachingProvider({
            calcCount++
            someValue()
        })

        then:
        calcCount == 0

        when:
        provider.get()

        then:
        calcCount == 1

        when:
        provider.get()
        provider.get()

        then:
        calcCount == 1
    }

    def "toString() does not realize value"() {
        given:
        def providerWithBadValue = new CachingProvider<String>({
            throw new RuntimeException()
        })

        expect:
        providerWithBadValue.toString() == "provider(?)"
        Providers.notDefined().toString() == "undefined"
    }

    def "rethrows exception if value calculation throws exception"() {
        def failure = new RuntimeException('Something went wrong')

        given:
        def provider = new CachingProvider({ throw failure })

        when:
        provider.get()

        then:
        def t = thrown(RuntimeException)
        t == failure
    }

    def "infers value type from callable implementation"() {
        def provider = new CachingProvider(callable)

        expect:
        provider.type == valueType

        where:
        callable                    | valueType
        new RawCallable()           | null
        new StringCallable()        | String
        new ParameterizedCallable() | null
        ({ 123 } as Callable)       | null
    }
}
