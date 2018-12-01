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
    String someValue() {
        return "s1"
    }

    @Override
    String someOtherValue() {
        return "s2"
    }

    def "toString() does not realize value"() {
        given:
        def providerWithBadValue = new DefaultProvider<String>({
            assert false : "never called"
        })

        expect:
        providerWithBadValue.toString() == "provider(?)"
        Providers.notDefined().toString() == "undefined"

    }

    def "throws exception if null value is retrieved for non-null get method"() {
        when:
        def provider = createProvider()
        provider.get()

        then:
        def t = thrown(IllegalStateException)
        t.message == "No value has been specified for this provider."

        when:
        provider = createProvider(null)
        provider.get()

        then:
        t = thrown(IllegalStateException)
        t.message == "No value has been specified for this provider."

        when:
        provider = createProvider(true)
        def value = provider.get()

        then:
        value
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

    static Provider createProvider() {
        new DefaultProvider({})
    }

    static Provider createProvider(value) {
        new DefaultProvider({ value })
    }
}
