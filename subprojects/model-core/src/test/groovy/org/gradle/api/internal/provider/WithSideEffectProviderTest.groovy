/*
 * Copyright 2022 the original author or authors.
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

import java.util.concurrent.atomic.AtomicInteger


class WithSideEffectProviderTest extends ProviderSpec<Integer> {

    @Override
    Provider providerWithNoValue() {
        return WithSideEffectProvider.of(Providers.notDefined(), Stub(ValueSupplier.SideEffect))
    }

    @Override
    Provider<Integer> providerWithValue(Integer value) {
        return WithSideEffectProvider.of(Providers.of(value), Stub(ValueSupplier.SideEffect))
    }

    @Override
    Class<Integer> type() {
        return Integer
    }

    @Override
    Integer someValue() {
        return 23
    }

    @Override
    Integer someOtherValue() {
        return 88
    }

    @Override
    Integer someOtherValue2() {
        return 146
    }

    @Override
    Integer someOtherValue3() {
        return 1024
    }

    @Override
    ManagedFactory managedFactory() {
        return new ManagedFactories.ProviderManagedFactory()
    }

    def "runs side effect when calling '#method' on changing provider"() {
        given:
        def sideEffect = Mock(ValueSupplier.SideEffect)
        def counter = new AtomicInteger(23)
        def parent = Providers.changing { counter.getAndIncrement() }
        def provider = parent.withSideEffect(sideEffect)

        when:
        provider.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead)
        provider.calculateExecutionTimeValue()

        then:
        0 * _ // no side effects when values are not unpacked

        when:
        counter.set(23)
        def unpackedValue = getter(provider, method, 88)
        then:
        unpackedValue == 23
        1 * sideEffect.execute(23)

        when:
        unpackedValue = getter(provider, method, 88)
        then:
        unpackedValue == 24
        1 * sideEffect.execute(24)
        0 * _

        where:
        method      | _
        "get"       | _
        "getOrNull" | _
        "getOrElse" | _
    }

    def "runs the side effect when changing provider is mapped with '#description'"() {
        given:
        def sideEffect = Mock(ValueSupplier.SideEffect)
        def counter = new AtomicInteger(23)
        def parent = Providers.changing { counter.getAndIncrement() }
        def provider = wrap(parent.withSideEffect(sideEffect)) { it * 2 }

        when:
        provider.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead)
        provider.calculateExecutionTimeValue()

        then:
        0 * _ // no side effects when values are not unpacked

        when:
        counter.set(23)
        def unpackedValue = provider.get()

        then:
        unpackedValue == 46
        1 * sideEffect.execute(23)
        0 * _

        when:
        unpackedValue = provider.get()

        then:
        unpackedValue == 48
        1 * sideEffect.execute(24)
        0 * _

        where:
        description               | wrap
        "Provider.map"            | { p, m -> p.map(m) }
        "Provider.flatMap"        | { p, m -> p.flatMap { Providers.of(m.call(it)) } }
        "TransformBackedProvider" | { p, m -> new TransformBackedProvider(null, p, m) }
        "MappingProvider"         | { p, m -> new MappingProvider(Integer, p, m) }
    }

    def "runs the side effect when changing provider is flat-mapped to a provider with side effect"() {
        given:
        def sideEffect1 = Mock(ValueSupplier.SideEffect)
        def sideEffect2 = Mock(ValueSupplier.SideEffect)
        def counter = new AtomicInteger(23)
        def parent = Providers.changing { counter.getAndIncrement() }.withSideEffect(sideEffect1)
        def provider = Providers.internal(parent.flatMap { Providers.of(it * 2).withSideEffect(sideEffect2) })

        when:
        provider.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead)
        provider.calculateExecutionTimeValue()

        then:
        0 * _ // no side effects when calculating wrapped values

        when:
        counter.set(23)
        def unpackedValue = provider.get()

        then:
        unpackedValue == 46
        // Side effect of the provider returned by transform is executed first.
        // See FlatMapProvider for details
        1 * sideEffect2.execute(46)

        then: // ensure invocation order
        1 * sideEffect1.execute(23)
        0 * _

        when:
        unpackedValue = provider.get()

        then:
        unpackedValue == 48
        1 * sideEffect2.execute(48)

        then: // ensure invocation order
        1 * sideEffect1.execute(24)
        0 * _
    }

    def "runs side effects for zipped providers when #description value"() {
        given:
        def leftSideEffect = Mock(ValueSupplier.SideEffect)
        def rightSideEffect = Mock(ValueSupplier.SideEffect)
        def zippedSideEffect = Mock(ValueSupplier.SideEffect)
        def leftWithSideEffect = Providers.ofNullable(leftValue).withSideEffect(leftSideEffect)
        def rightWithSideEffect = Providers.ofNullable(rightValue).withSideEffect(rightSideEffect)
        def zipped = leftWithSideEffect.zip(rightWithSideEffect) { a, b ->
            a == Integer.MAX_VALUE ? null : (a + b)
        } as ProviderInternal<Integer>
        def provider = zipped.withSideEffect(zippedSideEffect)

        when:
        provider.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead)
        provider.calculateExecutionTimeValue()

        then:
        0 * _ // no side effects when values are not unpacked

        when:
        def result = provider.getOrNull()

        then:
        result == expectedResult
        leftRuns * leftSideEffect.execute(leftValue)

        then: // ensure ordering
        rightRuns * rightSideEffect.execute(rightValue)

        then: // ensure ordering
        (leftRuns + rightRuns > 0 ? 1 : 0) * zippedSideEffect.execute(expectedResult)
        0 * _

        where:
        description                            | leftValue         | leftRuns | rightValue | rightRuns | expectedResult
        "both have"                            | 23                | 1        | 88         | 1         | 23 + 88
        "both have (but zip provider missing)" | Integer.MAX_VALUE | 0        | 88         | 0         | null
        "only left has"                        | 23                | 0        | null       | 0         | null
        "only right has"                       | null              | 0        | 88         | 0         | null
        "none have"                            | null              | 0        | null       | 0         | null
    }

    def "carries the side effect in the execution time value for a provider of a fixed value"() {
        given:
        def sideEffect = Mock(ValueSupplier.SideEffect)
        def parent = Providers.of(23)
        def provider = parent.withSideEffect(sideEffect)

        when:
        def value = provider.calculateExecutionTimeValue()
        value.fixedValue == 23

        then:
        0 * _

        when:
        value.toValue().get()

        then:
        1 * sideEffect.execute(23)
        0 * _
    }

    def "carries the side effect in the execution time value for a provider of a changing value"() {
        given:
        def sideEffect = Mock(ValueSupplier.SideEffect)
        def counter = new AtomicInteger(23)
        def parent = Providers.changing { counter.getAndIncrement() }
        def provider = parent.withSideEffect(sideEffect)

        when:
        def value = provider.calculateExecutionTimeValue()
        def changingValue = value.getChangingValue()

        then:
        0 * _

        when:
        changingValue.get()

        then:
        1 * sideEffect.execute(23)

        when:
        changingValue.get()

        then:
        1 * sideEffect.execute(24)
        0 * _
    }
}
