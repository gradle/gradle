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

package org.gradle.api.internal.provider

import spock.lang.Specification

/**
 * Contract for {@link ProviderInternal#explain(boolean)} per provider kind.
 *
 * <p>These tests are expected to FAIL until the per-kind {@code explain()} implementations
 * land. Each test defines what the implementation must produce for that kind of provider.</p>
 */
class ProviderExplainContractSpec extends Specification {

    private static final boolean EAGER = false
    private static final boolean LAZY = true

    // --- Fixed value providers ---

    def "fixed value provider is FIXED with hasValue=true (lazy=#lazy)"() {
        given:
        def provider = Providers.of("hello") as ProviderInternal

        when:
        def desc = provider.explain(lazy)

        then:
        desc.kind() == ProviderDescription.Kind.FIXED
        desc.hasValue()
        desc.sources().isEmpty()

        where:
        lazy << [EAGER, LAZY]
    }

    // --- Properties ---

    def "DefaultProperty is PROPERTY with baseType and optional displayName (lazy=#lazy)"() {
        given:
        def host = Mock(PropertyHost)
        def property = new DefaultProperty<String>(host, String)

        when: "no value, no display name"
        def desc = property.explain(lazy)

        then:
        desc.kind() == ProviderDescription.Kind.PROPERTY
        !desc.hasValue()
        desc.kindSpecificData()["baseType"] == String

        where:
        lazy << [EAGER, LAZY]
    }

    // --- Single-source derived providers ---

    def ".map() returns a MAPPED description with the source description (lazy=#lazy)"() {
        given:
        def mapped = Providers.of("hello").map { it.toUpperCase() }

        when:
        def desc = ((ProviderInternal) mapped).explain(lazy)

        then:
        desc.kind() == ProviderDescription.Kind.MAPPED
        desc.sources().size() == 1

        where:
        lazy << [EAGER, LAZY]
    }

    def ".flatMap() returns a FLAT_MAPPED description with the source description (lazy=#lazy)"() {
        given:
        def flatMapped = Providers.of("hello").flatMap { Providers.of(it.toUpperCase()) }

        when:
        def desc = ((ProviderInternal) flatMapped).explain(lazy)

        then:
        desc.kind() == ProviderDescription.Kind.FLAT_MAPPED
        desc.sources().size() == 1

        where:
        lazy << [EAGER, LAZY]
    }

    def ".filter() returns a FILTERED description with the source description (lazy=#lazy)"() {
        given:
        def filtered = Providers.of("hello").filter { true }

        when:
        def desc = ((ProviderInternal) filtered).explain(lazy)

        then:
        desc.kind() == ProviderDescription.Kind.FILTERED
        desc.sources().size() == 1

        where:
        lazy << [EAGER, LAZY]
    }

    // --- Multi-source derived providers ---

    def ".orElse() returns an OR_ELSE description with both branches in sources (lazy=#lazy)"() {
        given:
        def host = Mock(PropertyHost)
        def left = new DefaultProperty<String>(host, String) // missing
        def orElse = left.orElse(Providers.of("fallback"))

        when:
        def desc = ((ProviderInternal) orElse).explain(lazy)

        then:
        desc.kind() == ProviderDescription.Kind.OR_ELSE
        desc.sources().size() == 2

        where:
        lazy << [EAGER, LAZY]
    }

    def ".zip() returns a ZIP description with both branches in sources (lazy=#lazy)"() {
        given:
        def zipped = Providers.of("a").zip(Providers.of("b")) { l, r -> l + r }

        when:
        def desc = ((ProviderInternal) zipped).explain(lazy)

        then:
        desc.kind() == ProviderDescription.Kind.ZIP
        desc.sources().size() == 2

        where:
        lazy << [EAGER, LAZY]
    }

}
