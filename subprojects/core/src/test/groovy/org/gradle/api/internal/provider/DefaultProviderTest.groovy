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
import org.gradle.internal.UncheckedException
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.api.internal.provider.DefaultProvider.NON_NULL_VALUE_EXCEPTION_MESSAGE

class DefaultProviderTest extends Specification {

    @Unroll
    def "can compare string representation with other instance returning value #value"() {
        given:
        boolean immutableProviderValue1 = true
        def provider1 = createProvider(immutableProviderValue1)
        def provider2 = createProvider(value)

        expect:
        (provider1.toString() == provider2.toString()) == stringRepresentation
        provider1.toString() == "value: $immutableProviderValue1"
        provider2.toString() == "value: $value"

        where:
        value | stringRepresentation
        true  | true
        false | false
        null  | false
    }

    def "throws exception if null value is retrieved for non-null get method"() {
        when:
        def provider = createProvider()
        provider.get()

        then:
        def t = thrown(IllegalStateException)
        t.message == NON_NULL_VALUE_EXCEPTION_MESSAGE

        when:
        provider = createProvider(null)
        provider.get()

        then:
        t = thrown(IllegalStateException)
        t.message == NON_NULL_VALUE_EXCEPTION_MESSAGE

        when:
        provider = createProvider(true)
        def value = provider.get()

        then:
        value
    }

    def "returns value or null for get or null method"() {
        when:
        def provider = createProvider()

        then:
        !provider.isPresent()
        provider.getOrNull() == null

        when:
        provider = createProvider(true)

        then:
        provider.isPresent()
        provider.getOrNull()
    }

    def "rethrows exception if value calculation throws exception"() {
        given:
        def provider = new DefaultProvider({ throw new RuntimeException('Something went wrong') })

        when:
        provider.get()

        then:
        def t = thrown(UncheckedException)
        t.cause.message == 'Something went wrong'
    }

    static Provider createProvider() {
        new DefaultProvider({})
    }

    static Provider createProvider(value) {
        new DefaultProvider({ value })
    }
}
