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

import java.util.function.Predicate

abstract class ProviderSpec<T> extends Specification implements ProviderAssertions {
    abstract Provider<T> providerWithValue(T value)

    abstract Provider<T> providerWithNoValue()

    abstract T someValue()

    abstract T someOtherValue()

    abstract T someOtherValue2()

    abstract T someOtherValue3()

    abstract Class<T> type()

    abstract ManagedFactory managedFactory()

    boolean isNoValueProviderImmutable() {
        return false
    }

    String getDisplayName() {
        return "this provider"
    }

    def setup() {
        def values = [someValue(), someOtherValue(), someOtherValue2(), someOtherValue3()]
        assert values.unique(false) == values
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
        def t = thrown(MissingValueException)
        t.message == "Cannot query the value of ${displayName} because it has no value available."
    }

    def "mapped provider uses the result of transformer as its value"() {
        def transform = Mock(Transformer)

        given:
        def provider = providerWithValue(someValue())

        when:
        def mapped = provider.map(transform)

        then:
        0 * transform._

        when:
        def present = mapped.present

        then:
        present

        and:
        1 * transform.transform(someValue()) >> someOtherValue()
        0 * transform._

        when:
        def result = mapped.get()

        then:
        result == someOtherValue()

        and:
        1 * transform.transform(someValue()) >> someOtherValue()
        0 * transform._

        when:
        assert mapped.getOrNull() == someOtherValue()
        assert mapped.getOrElse(someValue()) == someOtherValue()

        then:
        2 * transform.transform(someValue()) >> someOtherValue()
        0 * transform._
    }

    def "mapped provider has no value when transformer returns null"() {
        given:
        def transform = Mock(Transformer)
        def provider = providerWithValue(someValue())

        when:
        def mapped = provider.map(transform)

        then:
        0 * transform._

        when:
        def present = mapped.present

        then:
        !present

        and:
        1 * transform.transform(someValue()) >> null
        0 * transform._

        when:
        mapped.get()

        then:
        1 * transform.transform(someValue()) >> null
        0 * transform._

        and:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot query the value of this provider because it has no value available.'

        when:
        def result = mapped.get()

        then:
        result == someOtherValue()

        and:
        1 * transform.transform(someValue()) >> someOtherValue()
        0 * transform._
    }

    def "filtering provider uses the result of the provider when predicate returns true"() {
        def predicate = Mock(Predicate)

        given:
        def provider = providerWithValue(someValue())

        when:
        def filtered = provider.filter(predicate)

        then:
        0 * predicate._

        when:
        def present = filtered.present

        then:
        present

        and:
        1 * predicate.test(someValue()) >> true
        0 * predicate._

        when:
        def result = filtered.get()

        then:
        result == someValue()

        and:
        1 * predicate.test(someValue()) >> true
        0 * predicate._

        when:
        assert filtered.getOrNull() == someValue()
        assert filtered.getOrElse(someOtherValue()) == someValue()

        then:
        2 * predicate.test(someValue()) >> true
        0 * predicate._
    }

    def "filtering provider has no value when predicate returns false"() {
        given:
        def predicate = Mock(Predicate)
        def provider = providerWithValue(someValue())

        when:
        def filtered = provider.filter(predicate)

        then:
        0 * predicate._

        when:
        def present = filtered.present

        then:
        !present

        and:
        1 * predicate.test(someValue()) >> false
        0 * predicate._

        when:
        filtered.get()

        then:
        1 * predicate.test(someValue()) >> false
        0 * predicate._

        and:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot query the value of this provider because it has no value available.'

        when:
        assert filtered.getOrNull() == null
        assert filtered.getOrElse(someOtherValue()) == someOtherValue()

        then:
        2 * predicate.test(someValue()) >> false
        0 * predicate._
    }

    def "can chain mapped providers"() {
        def transform1 = Mock(Transformer)
        def transform2 = Mock(Transformer)
        def transform3 = Mock(Transformer)

        given:
        def provider = providerWithValue(someValue())

        when:
        def mapped1 = provider.map(transform1)
        def mapped2 = mapped1.map(transform2)
        def mapped3 = mapped2.map(transform3)

        then:
        0 * _

        when:
        def present = mapped3.present

        then:
        present

        and:
        1 * transform1.transform(someValue()) >> someOtherValue()
        1 * transform2.transform(someOtherValue()) >> someOtherValue2()
        1 * transform3.transform(someOtherValue2()) >> someOtherValue3()
        0 * _

        when:
        def result = mapped3.get()

        then:
        result == someOtherValue3()

        and:
        1 * transform1.transform(someValue()) >> someOtherValue()
        1 * transform2.transform(someOtherValue()) >> someOtherValue2()
        1 * transform3.transform(someOtherValue2()) >> someOtherValue3()
        0 * _
    }

    def "can chain mapped providers with filtering providers"() {
        def transform1 = Mock(Transformer)
        def predicate = Mock(Predicate)
        def transform2 = Mock(Transformer)

        given:
        def provider = providerWithValue(someValue())

        when:
        def result1 = provider.map(transform1)
        def result2 = result1.filter(predicate)
        def result3 = result2.map(transform2)

        then:
        0 * _

        when:
        def present = result3.present

        then:
        present

        and:
        1 * transform1.transform(someValue()) >> someOtherValue()
        1 * predicate.test(someOtherValue()) >> true
        1 * transform2.transform(someOtherValue()) >> someOtherValue3()
        0 * _

        when:
        def result = result3.get()

        then:
        result == someOtherValue3()

        and:
        1 * transform1.transform(someValue()) >> someOtherValue()
        1 * predicate.test(someOtherValue()) >> true
        1 * transform2.transform(someOtherValue()) >> someOtherValue3()
        0 * _
    }

    def "flat mapped provider uses the result of transformer as its value"() {
        def transformer = Mock(Transformer)

        given:
        def provider = providerWithValue(someValue())

        when:
        def mapped = provider.flatMap(transformer)

        then:
        0 * _

        when:
        def present = mapped.present

        then:
        present

        and:
        1 * transformer.transform(someValue()) >> Providers.of(someOtherValue())
        0 * _

        when:
        def result = mapped.get()

        then:
        result == someOtherValue()

        and:
        1 * transformer.transform(someValue()) >> providerWithValue(someOtherValue())
        0 * _

        when:
        assert mapped.getOrNull() == someOtherValue()
        assert mapped.getOrElse(someValue()) == someOtherValue()

        then:
        2 * transformer.transform(someValue()) >> Providers.of(someOtherValue())
        0 * _
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

    def "flat mapped provider has no value when transformer returns null"() {
        given:
        def transform = Mock(Transformer)
        def provider = providerWithValue(someValue())
        def mapped = provider.flatMap(transform)

        when:
        def present = mapped.present

        then:
        !present

        and:
        1 * transform.transform(someValue()) >> null
        0 * transform._

        when:
        mapped.get()

        then:
        1 * transform.transform(someValue()) >> null
        0 * transform._

        and:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot query the value of this provider because it has no value available.'

        when:
        def result = mapped.get()

        then:
        result == someOtherValue()

        and:
        1 * transform.transform(someValue()) >> providerWithValue(someOtherValue())
        0 * transform._
    }

    def "mapped provider does not use transformer when source has no value"() {
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
        def t = thrown(MissingValueException)
        t.message == "Cannot query the value of this provider because it has no value available."
    }

    def "flat mapped provider does not use transformer when source has no value"() {
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
        def t = thrown(MissingValueException)
        t.message == "Cannot query the value of this provider because it has no value available."
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
        def broken = brokenSupplier()
        def supplier = Providers.of(someOtherValue())

        def present = providerWithValue(someValue())
        def usesValue = present.orElse(broken)

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

        def notPresent = providerWithNoValue()
        def usesDefaultValue = notPresent.orElse(supplier)
        !usesDefaultValue.present
        usesDefaultValue.getOrNull() == null

        when:
        notPresent.get()

        then:
        def e = thrown(MissingValueException)
        e.message == "Cannot query the value of ${displayName} because it has no value available."
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

    def "runs side effect when calling '#method' on provider with value and attached side effect"() {
        def sideEffect = Mock(ValueSupplier.SideEffect)
        def parent = providerWithValue(someValue())

        when:
        def provider = parent.withSideEffect(sideEffect)
        def value = provider.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead)
        def executionTimeValue = provider.calculateExecutionTimeValue()
        then:
        0 * _ // no side effects when values are not unpacked

        when:
        def unpackedValue = value.get()
        then:
        unpackedValue == someValue()
        1 * sideEffect.execute(someValue())
        0 * _

        when:
        unpackedValue = executionTimeValue.toValue().get()
        then:
        unpackedValue == someValue()
        1 * sideEffect.execute(someValue())
        0 * _

        when:
        unpackedValue = getter(provider, method, someOtherValue())
        then:
        unpackedValue == someValue()
        1 * sideEffect.execute(someValue())
        0 * _

        where:
        method      | _
        "get"       | _
        "getOrNull" | _
        "getOrElse" | _
    }

    def "does not run side effect when calling '#method' on provider with no value and attached side effect"() {
        given:
        def sideEffect = Mock(ValueSupplier.SideEffect)
        def parent = providerWithNoValue()
        def provider = parent.withSideEffect(sideEffect)

        when:
        provider.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead)
        provider.calculateExecutionTimeValue()

        then:
        0 * _ // no side effects when values are not unpacked

        when:
        def someValue = someValue()
        def unpackedValue = getter(provider, method, someValue)

        then:
        unpackedValue == expectedValue(someValue)
        0 * sideEffect.execute(_)
        0 * _

        where:
        method      | expectedValue
        "getOrNull" | { null }
        "getOrElse" | { it }
    }

    def "does not run side effect when calling 'get' on provider with no value and attached side effect"() {
        given:
        def sideEffect = Mock(ValueSupplier.SideEffect)
        def parent = providerWithNoValue()
        def provider = parent.withSideEffect(sideEffect)

        when:
        provider.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead)
        provider.calculateExecutionTimeValue()

        then:
        0 * _ // no side effects when values are not unpacked

        when:
        provider.get()

        then:
        thrown(IllegalStateException)
        0 * sideEffect.execute(_)
        0 * _
    }

    def "runs chained side effects when calling '#method' on provider with value"() {
        given:
        def sideEffect1 = Mock(ValueSupplier.SideEffect)
        def sideEffect2 = Mock(ValueSupplier.SideEffect)
        def parent = providerWithValue(someValue())
        def provider = parent.withSideEffect(sideEffect1).withSideEffect(sideEffect2)

        when:
        provider.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead)
        provider.calculateExecutionTimeValue()

        then:
        0 * _ // no side effects when values are not unpacked

        when:
        getter(provider, method, someOtherValue())

        then:
        1 * sideEffect1.execute(someValue())

        then: // required to ensure invocation ordering
        1 * sideEffect2.execute(someValue())
        0 * _

        where:
        method      | _
        "get"       | _
        "getOrNull" | _
        "getOrElse" | _
    }

    /**
     * Convenience method to call getters on a provider by the getter name.
     * <p>
     * The expected usage is:
     * <pre>
     * getter(provider, method, someOtherValue())
     * </pre>
     *
     * @param provider to call a getter on
     * @param getter is either 'get', 'getOrNull' or 'getOrElse'
     * @param orElse is passed to the getter only when it is 'getOrElse'
     * @return the value that the provider returned for the getter
     */
    static <T> T getter(Provider<T> provider, String getter, T orElse) {
        assert getter in ["get", "getOrNull", "getOrElse"]
        return getter == "getOrElse"
            ? provider."$getter"(orElse)
            : provider."$getter"()
    }

    /**
     * A test provider that always fails.
     */
    ProviderInternal<T> brokenSupplier() {
        return brokenSupplier(type())
    }

    /**
     * A test provider that always fails.
     */
    ProviderInternal brokenSupplier(Class type) {
        return new AbstractMinimalProvider() {
            @Override
            Class<T> getType() {
                return type
            }

            @Override
            protected ValueSupplier.Value<T> calculateOwnValue(ValueSupplier.ValueConsumer consumer) {
                throw new RuntimeException("broken!")
            }
        }
    }
}
