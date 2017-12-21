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
import spock.lang.Specification

abstract class ProviderSpec<T> extends Specification {
    abstract Provider<T> providerWithValue(T value)

    abstract Provider<T> providerWithNoValue()

    abstract T someValue()

    abstract T someOtherValue()

    def "can query value when it has as value"() {
        given:
        def provider = providerWithValue(someValue())

        expect:
        provider.present
        provider.get() == someValue()
        provider.getOrNull() == someValue()
        provider.getOrElse(someOtherValue()) == someValue()
    }

    def "cannot query value when it has none"() {
        given:
        def provider = providerWithNoValue()

        expect:
        !provider.present
        provider.getOrNull() == null
        provider.getOrElse(someValue()) == someValue()

        when:
        provider.get()

        then:
        def t = thrown(IllegalStateException)
        t.message == "No value has been specified for this provider."
    }

}
