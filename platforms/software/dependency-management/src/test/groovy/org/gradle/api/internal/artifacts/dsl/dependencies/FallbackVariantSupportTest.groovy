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

package org.gradle.api.internal.artifacts.dsl.dependencies

import org.gradle.api.attributes.FallbackVariant
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.attributes.matching.AttributeMatchingCandidate
import org.gradle.api.internal.attributes.matching.ImmutableAttributesBackedMatchingCandidate
import org.gradle.internal.component.model.DefaultMultipleCandidateResult
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

class FallbackVariantSupportTest extends Specification {

    def attributes = AttributeTestUtil.attributesFactory()
    def instantiator = TestUtil.objectInstantiator()
    def support = new FallbackVariantSupport(instantiator)
    def matcher = AttributeTestUtil.services().getMatcher(AttributeTestUtil.immutableSchema { support.configureSchema(it) }, ImmutableAttributesSchema.EMPTY)

    def "disambiguation rule selects #expected when consumer requests #requested from #candidates"() {
        given:
        def details = new DefaultMultipleCandidateResult<FallbackVariant>(requested == null ? null : value(requested), candidates.collect { value(it) } as Set)

        when:
        new FallbackVariantSupport.FallbackVariantDisambiguationRule().execute(details)

        then:
        details.hasResult() == (expected != null)
        expected == null || details.matches == [value(expected)] as Set

        where:
        requested             | candidates                                    | expected
        null                  | [FallbackVariant.TRUE, FallbackVariant.FALSE] | FallbackVariant.FALSE
        FallbackVariant.FALSE | [FallbackVariant.TRUE, FallbackVariant.FALSE] | FallbackVariant.FALSE
        FallbackVariant.TRUE  | [FallbackVariant.TRUE, FallbackVariant.FALSE] | FallbackVariant.TRUE
        null                  | [FallbackVariant.TRUE]                        | null
    }

    def "matcher prefers the non-fallback variant when the consumer does not request the attribute"() {
        given:
        def primary = candidate(FallbackVariant.TRUE)
        def secondary = candidate(FallbackVariant.FALSE)

        expect:
        matcher.matchMultipleCandidates([primary, secondary], ImmutableAttributes.EMPTY) == [secondary]
    }

    def "consumer is augmented with false only when it does not request the attribute"() {
        given:
        def requested = attributes.of(FallbackVariant.FALLBACK_VARIANT_ATTRIBUTE, value(FallbackVariant.TRUE))

        expect:
        support.augmentConsumerWithDefault(ImmutableAttributes.EMPTY, attributes).getAttribute(FallbackVariant.FALLBACK_VARIANT_ATTRIBUTE).name == FallbackVariant.FALSE
        support.augmentConsumerWithDefault(requested, attributes).is(requested)
    }

    private FallbackVariant value(String name) {
        instantiator.named(FallbackVariant, name)
    }

    private AttributeMatchingCandidate candidate(String fallbackValue) {
        new ImmutableAttributesBackedMatchingCandidate(attributes.of(FallbackVariant.FALLBACK_VARIANT_ATTRIBUTE, value(fallbackValue)))
    }
}
