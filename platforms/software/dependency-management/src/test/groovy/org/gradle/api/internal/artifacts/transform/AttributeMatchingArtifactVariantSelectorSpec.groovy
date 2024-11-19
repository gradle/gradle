/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform

import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet
import org.gradle.api.internal.attributes.AttributeSchemaServices
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.attributes.matching.AttributeMatcher
import org.gradle.internal.Describables
import org.gradle.internal.component.resolution.failure.exception.ArtifactSelectionException
import org.gradle.util.AttributeTestUtil
import spock.lang.Specification

class AttributeMatchingArtifactVariantSelectorSpec extends Specification {

    def consumerProvidedVariantFinder = Mock(ConsumerProvidedVariantFinder)
    def attributeMatcher = Mock(AttributeMatcher)
    def consumerSchema = Mock(ImmutableAttributesSchema)
    def schemaServices = Mock(AttributeSchemaServices) {
        getMatcher(_, _) >> attributeMatcher
    }
    def attributesFactory = AttributeTestUtil.attributesFactory()
    def requestedAttributes = AttributeTestUtil.attributes(['artifactType': 'jar'])

    def variant = Mock(ResolvedVariant) {
        getAttributes() >> AttributeTestUtil.attributes(['artifactType': 'jar'])
        asDescribable() >> Describables.of("mock resolved variant")
    }
    def otherVariant = Mock(ResolvedVariant) {
        getAttributes() >> AttributeTestUtil.attributes(['artifactType': 'classes'])
        asDescribable() >> Describables.of("mock other resolved variant")
    }
    def yetAnotherVariant = Mock(ResolvedVariant) {
        getAttributes() >> AttributeTestUtil.attributes(['artifactType': 'foo'])
        asDescribable() >> Describables.of("mock another resolved variant")
    }

    def failureProcessor = DependencyManagementTestUtil.newFailureHandler()

    def 'direct match on variant means no finder interaction'() {
        given:
        def resolvedArtifactSet = Mock(ResolvedArtifactSet)
        def variants = [variant]
        def selector = newSelector()

        when:
        def result = selector.select(variantSetOf(variants), requestedAttributes, false)

        then:
        result == resolvedArtifactSet
        1 * attributeMatcher.matchMultipleCandidates(_, _) >> [variant]
        1 * variant.getArtifacts() >> resolvedArtifactSet
        0 * consumerProvidedVariantFinder._
    }

    def 'multiple match on variant results in ambiguous exception'() {
        given:
        def variantSet = variantSetOf([variant, otherVariant])
        def selector = newSelector()

        when:
        def result = selector.select(variantSet, requestedAttributes, false)

        then:
        result instanceof BrokenResolvedArtifactSet
        result.failure instanceof ArtifactSelectionException

        1 * variantSet.getProducerSchema() >> ImmutableAttributesSchema.EMPTY
        1 * variantSet.getOverriddenAttributes() >> ImmutableAttributes.EMPTY
        1 * attributeMatcher.matchMultipleCandidates(_, _) >> [variant, otherVariant]
        2 * attributeMatcher.isMatchingValue(_, _, _) >> true
        0 * consumerProvidedVariantFinder._
    }

    def 'does not perform schema disambiguation against a single transform result'() {
        given:
        def transformed = Mock(ResolvedArtifactSet)
        def variants = [variant]
        def transformedVariants = transformedVariants(variants)
        def selector = newSelector()
        def candidates = variantSetOf(variants)

        when:
        def result = selector.select(candidates, requestedAttributes, false)

        then:
        result == transformed

        1 * attributeMatcher.matchMultipleCandidates(_, _) >> Collections.emptyList()
        1 * consumerProvidedVariantFinder.findTransformedVariants(variants, requestedAttributes) >> transformedVariants
        1 * candidates.transformCandidate(variant, transformedVariants[0].getTransformedVariantDefinition()) >> transformed
        0 * attributeMatcher._
    }

    def 'can leverage schema disambiguation'() {
        given:
        def transformed = Mock(ResolvedArtifactSet)
        def variants = [variant, otherVariant, yetAnotherVariant]
        def transformedVariants = transformedVariants(variants)
        def selector = newSelector()
        def candidates = variantSetOf(variants)

        when:
        def result = selector.select(candidates, requestedAttributes, false)

        then:
        result == transformed

        1 * attributeMatcher.matchMultipleCandidates(_, _) >> Collections.emptyList()
        1 * consumerProvidedVariantFinder.findTransformedVariants(variants, requestedAttributes) >> transformedVariants
        1 * attributeMatcher.matchMultipleCandidates(_, _) >> [transformedVariants[resultNum]]
        1 * candidates.transformCandidate(variants[resultNum], transformedVariants[resultNum].getTransformedVariantDefinition()) >> transformed

        where:
        resultNum << [0, 1, 2]
    }

    def 'exception for ambiguous transformations'() {
        given:
        def variants = [variant, otherVariant]
        def transformedVariants = transformedVariants(variants)
        def selector = newSelector()

        when:
        def result = selector.select(variantSetOf(variants), requestedAttributes, false)

        then:
        result instanceof BrokenResolvedArtifactSet
        result.failure instanceof ArtifactSelectionException

        1 * attributeMatcher.matchMultipleCandidates(_, _) >> Collections.emptyList()
        1 * consumerProvidedVariantFinder.findTransformedVariants(variants, requestedAttributes) >> transformedVariants
        1 * attributeMatcher.matchMultipleCandidates(_, _) >> transformedVariants
    }

    private AttributeMatchingArtifactVariantSelector newSelector() {
        new AttributeMatchingArtifactVariantSelector(
            consumerSchema,
            consumerProvidedVariantFinder,
            attributesFactory,
            schemaServices,
            failureProcessor
        )
    }

    ResolvedVariantSet variantSetOf(List<ResolvedVariant> variants) {
        return Mock(ResolvedVariantSet) {
            asDescribable() >> Describables.of("mock producer")
            getCandidates() >> variants
            getOverriddenAttributes() >> ImmutableAttributes.EMPTY
        }
    }

    private List<TransformedVariant> transformedVariants(List<ResolvedVariant> variants) {
        variants.collect {
            new TransformedVariant(it, Mock(VariantDefinition) {
                transformChain >> Mock(TransformChain)
                targetAttributes >> ImmutableAttributes.EMPTY
            })
        }
    }
}
