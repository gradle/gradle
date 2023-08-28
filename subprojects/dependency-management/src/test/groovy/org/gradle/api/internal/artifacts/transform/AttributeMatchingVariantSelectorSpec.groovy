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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.Describables
import org.gradle.internal.component.AmbiguousVariantSelectionException
import org.gradle.internal.component.SelectionFailureHandler
import org.gradle.internal.component.model.AttributeMatcher
import org.gradle.util.AttributeTestUtil
import spock.lang.Specification

import static org.gradle.api.problems.TestProblemsUtil.createTestProblems

class AttributeMatchingVariantSelectorSpec extends Specification {

    def consumerProvidedVariantFinder = Mock(ConsumerProvidedVariantFinder)
    def transformedVariantFactory = Mock(TransformedVariantFactory)
    def dependenciesResolverFactory = Mock(TransformUpstreamDependenciesResolverFactory)
    def attributeMatcher = Mock(AttributeMatcher)
    def attributesSchema = Mock(AttributesSchemaInternal) {
        withProducer(_) >> attributeMatcher
        getConsumerDescribers() >> []
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

    def factory = Mock(VariantSelector.Factory)
    def failureProcessor = new SelectionFailureHandler(createTestProblems())

    def 'direct match on variant means no finder interaction'() {
        given:
        def resolvedArtifactSet = Mock(ResolvedArtifactSet)
        def variants = [variant]
        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformedVariantFactory, requestedAttributes, false, false, dependenciesResolverFactory, failureProcessor)

        when:
        def result = selector.select(variantSetOf(variants), factory)

        then:
        result == resolvedArtifactSet
        1 * attributeMatcher.matches(_, _, _) >> [variant]
        1 * variant.getArtifacts() >> resolvedArtifactSet
        0 * consumerProvidedVariantFinder._
    }

    def 'multiple match on variant results in ambiguous exception'() {
        given:
        def variantSet = variantSetOf([variant, otherVariant])
        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformedVariantFactory, requestedAttributes, false, false, dependenciesResolverFactory, failureProcessor)

        when:
        def result = selector.select(variantSet, factory)

        then:
        result instanceof BrokenResolvedArtifactSet
        result.failure instanceof AmbiguousVariantSelectionException

        1 * variantSet.getSchema() >> attributesSchema
        1 * variantSet.getOverriddenAttributes() >> ImmutableAttributes.EMPTY
        2 * attributeMatcher.matches(_, _, _) >> [variant, otherVariant]
        2 * attributeMatcher.isMatching(_, _, _) >> true
        0 * consumerProvidedVariantFinder._
    }

    def 'does not perform schema disambiguation against a single transform result'() {
        given:
        def transformed = Mock(ResolvedArtifactSet)
        def variants = [variant]
        def transformedVariants = transformedVariants(variants)
        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformedVariantFactory, requestedAttributes, false, false, dependenciesResolverFactory, failureProcessor)

        when:
        def result = selector.select(variantSetOf(variants), factory)

        then:
        result == transformed

        1 * attributeMatcher.matches(_, _, _) >> Collections.emptyList()
        1 * consumerProvidedVariantFinder.findTransformedVariants(variants, requestedAttributes) >> transformedVariants
        1 * factory.asTransformed(variant, transformedVariants[0].getTransformedVariantDefinition(), dependenciesResolverFactory, transformedVariantFactory) >> transformed
        0 * attributeMatcher._
    }

    def 'can leverage schema disambiguation'() {
        given:
        def transformed = Mock(ResolvedArtifactSet)
        def variants = [variant, otherVariant, yetAnotherVariant]
        def transformedVariants = transformedVariants(variants)
        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformedVariantFactory, requestedAttributes, false, false, dependenciesResolverFactory, failureProcessor)

        when:
        def result = selector.select(variantSetOf(variants), factory)

        then:
        result == transformed

        1 * attributeMatcher.matches(_, _, _) >> Collections.emptyList()
        1 * consumerProvidedVariantFinder.findTransformedVariants(variants, requestedAttributes) >> transformedVariants
        1 * attributeMatcher.matches(_, _, _) >> [transformedVariants[resultNum]]
        1 * factory.asTransformed(variants[resultNum], transformedVariants[resultNum].getTransformedVariantDefinition(), dependenciesResolverFactory, transformedVariantFactory) >> transformed

        where:
        resultNum << [0, 1, 2]
    }

    def 'exception for ambiguous transformations'() {
        given:
        def variants = [variant, otherVariant]
        def transformedVariants = transformedVariants(variants)
        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformedVariantFactory, requestedAttributes, false, false, dependenciesResolverFactory, failureProcessor)

        when:
        def result = selector.select(variantSetOf(variants), factory)

        then:
        result instanceof BrokenResolvedArtifactSet
        result.failure instanceof AmbiguousTransformException

        1 * attributeMatcher.matches(_, _, _) >> Collections.emptyList()
        1 * consumerProvidedVariantFinder.findTransformedVariants(variants, requestedAttributes) >> transformedVariants
        1 * attributeMatcher.matches(_, _, _) >> transformedVariants
    }

    ResolvedVariantSet variantSetOf(List<ResolvedVariant> variants) {
        return Mock(ResolvedVariantSet) {
            asDescribable() >> Describables.of("mock producer")
            getVariants() >> variants
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
