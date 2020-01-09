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

import org.gradle.api.Transformer
import org.gradle.api.provider.Provider
import org.gradle.internal.state.Managed
import org.gradle.internal.state.ManagedFactory
import spock.lang.Specification

abstract class ProviderSpec<T> extends Specification {
    abstract Provider<T> providerWithValue(T value)

    abstract Provider<T> providerWithNoValue()

    abstract T someValue()

    abstract T someOtherValue()

    abstract ManagedFactory managedFactory()

    boolean isNoValueProviderImmutable() {
        return false
    }

    String getDisplayName() {
        return "this provider"
    }

    def "can query value when it has as value"() {
        given:
        def provider = providerWithValue(someValue())

        expect:
        provider.present
        provider.get() == someValue()
        provider.getOrNull() == someValue()
        provider.getOrElse(someOtherValue()) == someValue()
    }

    def "mapped provider returns result of transformer"() {
        def transform = Mock(Transformer)

        given:
        def provider = providerWithValue(someValue())
        def mapped = provider.map(transform)

        when:
        mapped.present

        then:
        0 * transform._

        when:
        mapped.get() == someOtherValue()
        mapped.getOrNull() == someOtherValue()
        mapped.getOrElse(someValue()) == someOtherValue()

        then:
        _ * transform.transform(someValue()) >> someOtherValue()
    }

    def "mapped provider fails when transformer returns null"() {
        given:
        def transform = Mock(Transformer)
        def provider = providerWithValue(someValue())

        when:
        def mapped = provider.map(transform)

        then:
        mapped.present
        0 * transform._

        when:
        mapped.get()

        then:
        1 * transform.transform(someValue()) >> null
        0 * transform._

        and:
        def e = thrown(IllegalStateException)
        e.message == 'Transformer for this provider returned a null value.'

        when:
        mapped.get()

        then:
        1 * transform.transform(someValue()) >> someOtherValue()
        0 * transform._
    }

    def "flat mapped provider returns result of transformer"() {
        def transformer = Stub(Transformer)
        transformer.transform(someValue()) >> providerWithValue(someOtherValue())

        given:
        def provider = providerWithValue(someValue())
        def mapped = provider.flatMap(transformer)

        expect:
        mapped.present
        mapped.get() == someOtherValue()
        mapped.getOrNull() == someOtherValue()
        mapped.getOrElse(someValue()) == someOtherValue()
    }

    def "flat mapped provider returns result of transformer when the result has no value"() {
        def transformer = Stub(Transformer)
        transformer.transform(someValue()) >> providerWithNoValue()

        given:
        def provider = providerWithValue(someValue())
        def mapped = provider.flatMap(transformer)

        expect:
        !mapped.present
        mapped.getOrNull() == null
        mapped.getOrElse(someOtherValue()) == someOtherValue()
    }

    def "flat mapped provider fails when transformer returns null"() {
        given:
        def transform = Mock(Transformer)
        def provider = providerWithValue(someValue())
        def mapped = provider.flatMap(transform)

        when:
        mapped.get()

        then:
        1 * transform.transform(someValue()) >> null
        0 * transform._

        and:
        def e = thrown(IllegalStateException)
        e.message == 'Transformer for this provider returned a null value.'

        when:
        mapped.get()

        then:
        1 * transform.transform(someValue()) >> providerWithValue(someOtherValue())
        0 * transform._
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
        t.message == "No value has been specified for ${displayName}."
    }

    def "mapped provider with no value does not use transformer"() {
        def transformer = { throw new RuntimeException() } as Transformer

        given:
        def provider = providerWithNoValue()
        def mapped = provider.map(transformer)

        expect:
        !mapped.present
        mapped.getOrNull() == null
        mapped.getOrElse(someValue()) == someValue()

        when:
        mapped.get()

        then:
        def t = thrown(IllegalStateException)
        t.message == "No value has been specified for ${displayName}."
    }

    def "flat mapped provider with no value does not use transformer"() {
        def transformer = { throw new RuntimeException() } as Transformer

        given:
        def provider = providerWithNoValue()
        def mapped = provider.flatMap(transformer)

        expect:
        !mapped.present
        mapped.getOrNull() == null
        mapped.getOrElse(someValue()) == someValue()

        when:
        mapped.get()

        then:
        def t = thrown(IllegalStateException)
        t.message == "No value has been specified for ${displayName}."
    }

    def "can map to provider that uses value if present or a default value"() {
        expect:
        def present = providerWithValue(someValue())
        def usesValue = present.orElse(someOtherValue())
        usesValue.present
        usesValue.get() == someValue()

        def notPresent = providerWithNoValue()
        def usesDefaultValue = notPresent.orElse(someOtherValue())
        usesDefaultValue.present
        usesDefaultValue.get() == someOtherValue()
    }

    def "can map to provider that uses value if present or a default value from another provider"() {
        expect:
        def supplier = Providers.of(someOtherValue())

        def present = providerWithValue(someValue())
        def usesValue = present.orElse(supplier)

        usesValue.present
        usesValue.get() == someValue()

        def notPresent = providerWithNoValue()
        def usesDefaultValue = notPresent.orElse(supplier)

        usesDefaultValue.present
        usesDefaultValue.get() == someOtherValue()
    }

    def "can map to provider that uses value if present or a default value from another provider that does not have a value"() {
        expect:
        def supplier = Providers.notDefined()

        def present = providerWithValue(someValue())
        def usesValue = present.orElse(supplier)
        usesValue.present
        usesValue.get() == someValue()

        def notPresent = providerWithNoValue()
        def usesDefaultValue = notPresent.orElse(supplier)
        !usesDefaultValue.present
        usesDefaultValue.getOrNull() == null
    }

    def "can chain orElse"() {
        expect:
        def supplier1 = Providers.notDefined()
        def supplier2 = Providers.notDefined()
        def supplier3 = Providers.of(someValue())

        def notPresent = providerWithNoValue()
        def usesDefaultValue = notPresent.orElse(supplier1).orElse(supplier2).orElse(supplier3)
        usesDefaultValue.present
        usesDefaultValue.get() == someValue()
    }

    def "can unpack state and recreate instance when provider has no value"() {
        given:
        def provider = providerWithNoValue()

        expect:
        provider instanceof Managed
        provider.isImmutable() == noValueProviderImmutable
        def state = provider.unpackState()
        def copy = managedFactory().fromState(provider.publicType(), state)
        !copy.is(provider) || noValueProviderImmutable
        !copy.present
        copy.getOrNull() == null
    }

    def "can unpack state and recreate instance when provider has value"() {
        given:
        def provider = providerWithValue(someValue())

        expect:
        provider instanceof Managed
        !provider.isImmutable()
        def state = provider.unpackState()
        def copy = managedFactory().fromState(provider.publicType(), state)
        !copy.is(provider)
        copy.present
        copy.getOrNull() == someValue()
    }
}
