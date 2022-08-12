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

import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger


class WithSideEffectProviderTest extends Specification {

    def "runs side effect when calling '#method' on fixed value provider"() {
        given:
        def sideEffect = Mock(ValueSupplier.SideEffect)
        def parent = Providers.of(23)
        def provider = parent.withSideEffect(sideEffect)

        when:
        def unpackedValue = extract(provider)

        then:
        unpackedValue == 23
        1 * sideEffect.execute(23)
        0 * _

        where:
        method      | extract
        "get"       | { it.get() }
        "getOrNull" | { it.getOrNull() }
        "getOrElse" | { it.getOrElse(88) }
    }

    def "runs side effect when calling '#method' on changing value provider"() {
        given:
        def sideEffect = Mock(ValueSupplier.SideEffect)
        def counter = new AtomicInteger(23)
        def parent = Providers.changing { counter.getAndIncrement() }
        def provider = parent.withSideEffect(sideEffect)

        when:
        def unpackedValue = extract(provider)
        then:
        unpackedValue == 23
        1 * sideEffect.execute(23)

        when:
        unpackedValue = extract(provider)
        then:
        unpackedValue == 24
        1 * sideEffect.execute(24)
        0 * _

        where:
        method      | extract
        "get"       | { it.get() }
        "getOrNull" | { it.getOrNull() }
        "getOrElse" | { it.getOrElse(88) }
    }

    def "does not run side effect when calling '#method' on missing value provider"() {
        given:
        def sideEffect = Mock(ValueSupplier.SideEffect)
        def parent = Providers.notDefined()
        def provider = parent.withSideEffect(sideEffect)

        when:
        def unpackedValue = unpack(provider)

        then:
        unpackedValue == expectedValue
        0 * sideEffect.execute(_)
        0 * _

        where:
        method      | unpack               | expectedValue
        "getOrNull" | { it.getOrNull() }   | null
        "getOrElse" | { it.getOrElse(88) } | 88
    }

    def "does not run side effect when calling 'get' on missing value provider"() {
        given:
        def sideEffect = Mock(ValueSupplier.SideEffect)
        def parent = Providers.notDefined()
        def provider = parent.withSideEffect(sideEffect)

        when:
        provider.get()

        then:
        thrown(IllegalStateException)
        0 * sideEffect.execute(_)
        0 * _
    }

    def "chains side effects applied to the same provider"() {
        given:
        def sideEffect1 = Mock(ValueSupplier.SideEffect)
        def sideEffect2 = Mock(ValueSupplier.SideEffect)
        def parent = Providers.of(23)
        def provider = parent.withSideEffect(sideEffect1).withSideEffect(sideEffect2)

        when:
        extract(provider)

        then:
        1 * sideEffect1.execute(23)

        then: // required to ensure invocation ordering
        1 * sideEffect2.execute(23)
        0 * _

        where:
        method      | extract
        "get"       | { it.get() }
        "getOrNull" | { it.getOrNull() }
        "getOrElse" | { it.getOrElse(88) }
    }

    def "runs the side effect when getting a value of a provider mapped with '#description'"() {
        given:
        def sideEffect = Mock(ValueSupplier.SideEffect)
        def counter = new AtomicInteger(23)
        def parent = Providers.changing { counter.getAndIncrement() }
        def provider = wrap(parent.withSideEffect(sideEffect)) { it * 2 }

        when:
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
        description                 | wrap
        "ProviderInternal.map"      | { p, m -> p.map(m) }
        "TransformerBackedProvider" | { p, m -> new TransformBackedProvider(m, p) }
        "MappingProvider"           | { p, m -> new MappingProvider(Integer, p, m) }
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
        0 * sideEffect.execute(_)

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
        0 * sideEffect.execute(_)

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
