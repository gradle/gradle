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
import org.gradle.api.internal.attributes.ImmutableAttributesFactory
import org.gradle.internal.Describables
import org.gradle.internal.component.AmbiguousVariantSelectionException
import org.gradle.internal.component.model.AttributeMatcher
import org.gradle.util.AttributeTestUtil
import spock.lang.Specification

class AttributeMatchingVariantSelectorSpec extends Specification {

    def consumerProvidedVariantFinder = Mock(ConsumerProvidedVariantFinder)
    def transformedVariantFactory = Mock(TransformedVariantFactory)
    def dependenciesResolverFactory = Mock(ExtraExecutionGraphDependenciesResolverFactory)
    def attributeMatcher = Mock(AttributeMatcher)
    def attributesSchema = Mock(AttributesSchemaInternal) {
        withProducer(_) >> attributeMatcher
        getConsumerDescribers() >> []
    }
    def attributesFactory = Mock(ImmutableAttributesFactory) {
        concat(_, _) >> { args -> return args[0] }
    }
    def requestedAttributes = AttributeTestUtil.attributes(['artifactType': 'jar'])
    def variantAttributes = AttributeTestUtil.attributes(['artifactType': 'jar'])
    def otherVariantAttributes = AttributeTestUtil.attributes(['artifactType': 'classes'])
    def variant = Mock(ResolvedVariant) {
        getAttributes() >> variantAttributes
        asDescribable() >> Describables.of("mock resolved variant")
    }
    def variantSet = Mock(ResolvedVariantSet) {
        asDescribable() >> Describables.of("mock producer")
        getVariants() >> [variant]
    }
    def factory = Mock(VariantSelector.Factory)

    def 'direct match on variant means no finder interaction'() {
        given:
        def resolvedArtifactSet = Mock(ResolvedArtifactSet)
        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformedVariantFactory, requestedAttributes, false, dependenciesResolverFactory)

        when:
        def result = selector.select(variantSet, factory)

        then:
        result == resolvedArtifactSet
        1 * attributeMatcher.matches(_, _, _) >> [variant]
        1 * variant.getArtifacts() >> resolvedArtifactSet
        0 * consumerProvidedVariantFinder._
    }

    def 'multiple match on variant results in ambiguous exception'() {
        given:
        def otherResolvedVariant = Mock(ResolvedVariant) {
            asDescribable() >> Describables.of('other mocked variant')
            getAttributes() >> otherVariantAttributes
        }
        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformedVariantFactory, requestedAttributes, false, dependenciesResolverFactory)

        when:
        def result = selector.select(variantSet, factory)

        then:
        result instanceof BrokenResolvedArtifactSet
        result.failure instanceof AmbiguousVariantSelectionException

        2 * variantSet.getSchema() >> attributesSchema
        2 * variantSet.getOverriddenAttributes() >> ImmutableAttributes.EMPTY
        2 * attributeMatcher.matches(_, _, _) >> [variant, otherResolvedVariant]
        2 * attributeMatcher.isMatching(_, _, _) >> true
        0 * consumerProvidedVariantFinder._
    }

    def 'selects matching variant and creates wrapper'() {
        given:
        def transformationStep = Mock(TransformationStep)
        def transformed = Mock(ResolvedArtifactSet)

        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformedVariantFactory, requestedAttributes, false, dependenciesResolverFactory)

        when:
        def result = selector.select(variantSet, factory)

        then:
        result == transformed

        1 * attributeMatcher.matches(_, _, _) >> Collections.emptyList()
        1 * consumerProvidedVariantFinder.collectConsumerVariants(variantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transformationStep, 1)
        }
        1 * factory.asTransformed(variant, {  it.targetAttributes == requestedAttributes && it.transformationStep == transformationStep }, dependenciesResolverFactory, transformedVariantFactory) >> transformed
    }

    def 'can disambiguate sub chains'() {
        given:
        def transformed = Mock(ResolvedArtifactSet)
        def otherVariant = Mock(ResolvedVariant) {
            getAttributes() >> otherVariantAttributes
            asDescribable() >> Describables.of("mock other resolved variant")
        }
        def multiVariantSet = Mock(ResolvedVariantSet) {
            asDescribable() >> Describables.of("mock multi producer")
            getVariants() >> [variant, otherVariant]
        }
        def transform1 = Mock(TransformationStep)
        def transform2 = Mock(TransformationStep)

        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformedVariantFactory, requestedAttributes, false, dependenciesResolverFactory)

        when:
        def result = selector.select(multiVariantSet, factory)

        then:
        result == transformed

        1 * attributeMatcher.matches(_, _, _) >> Collections.emptyList()
        1 * attributeMatcher.isMatching(requestedAttributes, requestedAttributes) >> true
        1 * consumerProvidedVariantFinder.collectConsumerVariants(variantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform1, 2)
        }
        1 * consumerProvidedVariantFinder.collectConsumerVariants(otherVariantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform2, 3)
        }
        1 * attributeMatcher.matches(_, _, _) >> { args -> args[0] }
        1 * factory.asTransformed(variant, { it.targetAttributes == requestedAttributes && it.transformationStep == transform1 }, dependenciesResolverFactory, transformedVariantFactory) >> transformed
    }

    def 'can disambiguate 2 equivalent chains by picking shortest'() {
        given:
        def transformed = Mock(ResolvedArtifactSet)
        def otherVariant = Mock(ResolvedVariant) {
            getAttributes() >> otherVariantAttributes
            asDescribable() >> Describables.of("mock other resolved variant")
        }
        def multiVariantSet = Mock(ResolvedVariantSet) {
            asDescribable() >> Describables.of("mock multi producer")
            getVariants() >> [variant, otherVariant]
        }
        def transform1 = Mock(TransformationStep)
        def transform2 = Mock(TransformationStep)
        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformedVariantFactory, requestedAttributes, false, dependenciesResolverFactory)

        when:
        def result = selector.select(multiVariantSet, factory)

        then:
        result == transformed

        1 * attributeMatcher.matches(_, _, _) >> Collections.emptyList()
        1 * attributeMatcher.isMatching(requestedAttributes, requestedAttributes) >> true
        1 * consumerProvidedVariantFinder.collectConsumerVariants(variantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform1, 2)
        }
        1 * consumerProvidedVariantFinder.collectConsumerVariants(otherVariantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform2, 3)
        }
        1 * attributeMatcher.matches(_, _, _) >> { args -> args[0] }
        1 * factory.asTransformed(variant, { it.targetAttributes == requestedAttributes && it.transformationStep == transform1 }, dependenciesResolverFactory, transformedVariantFactory) >> transformed
    }

    def 'can leverage schema disambiguation'() {
        given:
        def transformed = Mock(ResolvedArtifactSet)
        def otherVariant = Mock(ResolvedVariant) {
            getAttributes() >> otherVariantAttributes
            asDescribable() >> Describables.of("mock other resolved variant")
        }
        def multiVariantSet = Mock(ResolvedVariantSet) {
            asDescribable() >> Describables.of("mock multi producer")
            getVariants() >> [variant, otherVariant]
        }
        def transform1 = Mock(TransformationStep)
        def transform2 = Mock(TransformationStep)
        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformedVariantFactory, requestedAttributes, false, dependenciesResolverFactory)

        when:
        def result = selector.select(multiVariantSet, factory)

        then:
        result == transformed

        1 * attributeMatcher.matches(_, _, _) >> Collections.emptyList()
        1 * consumerProvidedVariantFinder.collectConsumerVariants(variantAttributes, requestedAttributes) >> { args ->
            match(otherVariantAttributes, transform1, 2)
        }
        1 * consumerProvidedVariantFinder.collectConsumerVariants(otherVariantAttributes, requestedAttributes) >> { args ->
            match(variantAttributes, transform2, 3)
        }
        1 * attributeMatcher.matches(_, _, _) >> { args -> [args[0].get(0)] }
        1 * factory.asTransformed(variant, { it.targetAttributes == otherVariantAttributes && it.transformationStep == transform1 }, dependenciesResolverFactory, transformedVariantFactory) >> transformed
    }

    def 'can disambiguate between three chains when one subset of both others'() {
        given:
        def transformed = Mock(ResolvedArtifactSet)
        def yetAnotherVariantAttributes = AttributeTestUtil.attributes(['artifactType': 'foo'])
        def otherVariant = Mock(ResolvedVariant) {
            getAttributes() >> otherVariantAttributes
            asDescribable() >> Describables.of("mock other resolved variant")
        }
        def yetAnotherVariant = Mock(ResolvedVariant) {
            getAttributes() >> yetAnotherVariantAttributes
            asDescribable() >> Describables.of("mock another resolved variant")
        }
        def multiVariantSet = Mock(ResolvedVariantSet) {
            asDescribable() >> Describables.of("mock multi producer")
            getVariants() >> [variant, otherVariant, yetAnotherVariant]
        }
        def transform1 = Mock(TransformationStep)
        def transform2 = Mock(TransformationStep)
        def transform3 = Mock(TransformationStep)
        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformedVariantFactory, requestedAttributes, false, dependenciesResolverFactory)

        when:
        def result = selector.select(multiVariantSet, factory)

        then:
        result == transformed

        1 * attributeMatcher.matches(_, _, _) >> Collections.emptyList()
        2 * attributeMatcher.isMatching(requestedAttributes, requestedAttributes) >> true
        1 * consumerProvidedVariantFinder.collectConsumerVariants(variantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform1, 3)
        }
        1 * consumerProvidedVariantFinder.collectConsumerVariants(otherVariantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform2, 3)
        }
        1 * consumerProvidedVariantFinder.collectConsumerVariants(yetAnotherVariantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform3, 2)
        }
        1 * attributeMatcher.matches(_, _, _) >> { args -> args[0] }
        1 * factory.asTransformed(yetAnotherVariant, { it.targetAttributes == requestedAttributes && it.transformationStep == transform3 }, dependenciesResolverFactory, transformedVariantFactory) >> transformed
    }

    def 'can disambiguate 3 equivalent chains by picking shortest'() {
        given:
        def transformed = Mock(ResolvedArtifactSet)
        def yetAnotherVariantAttributes = AttributeTestUtil.attributes(['artifactType': 'foo'])
        def otherVariant = Mock(ResolvedVariant) {
            getAttributes() >> otherVariantAttributes
            asDescribable() >> Describables.of("mock other resolved variant")
        }
        def yetAnotherVariant = Mock(ResolvedVariant) {
            getAttributes() >> yetAnotherVariantAttributes
            asDescribable() >> Describables.of("mock another resolved variant")
        }
        def multiVariantSet = Mock(ResolvedVariantSet) {
            asDescribable() >> Describables.of("mock multi producer")
            getVariants() >> [variant, otherVariant, yetAnotherVariant]
        }
        def transform1 = Mock(TransformationStep)
        def transform2 = Mock(TransformationStep)
        def transform3 = Mock(TransformationStep)
        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformedVariantFactory, requestedAttributes, false, dependenciesResolverFactory)

        when:
        def result = selector.select(multiVariantSet, factory)

        then:
        result == transformed

        1 * attributeMatcher.matches(_, _, _) >> Collections.emptyList()
        1 * consumerProvidedVariantFinder.collectConsumerVariants(variantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform1, 3)
        }
        1 * consumerProvidedVariantFinder.collectConsumerVariants(otherVariantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform2, 2)
        }
        1 * consumerProvidedVariantFinder.collectConsumerVariants(yetAnotherVariantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform3, 3)
        }
        2 * attributeMatcher.isMatching(requestedAttributes, requestedAttributes) >> true
        1 * attributeMatcher.matches(_, _, _) >> { args -> args[0] }
        1 * factory.asTransformed(otherVariant, { it.targetAttributes == requestedAttributes && it.transformationStep == transform2 }, dependenciesResolverFactory, transformedVariantFactory) >> transformed
    }

    def 'cannot disambiguate 3 chains when 2 different'() {
        given:
        def yetAnotherVariantAttributes = AttributeTestUtil.attributes(['artifactType': 'foo'])
        def otherVariant = Mock(ResolvedVariant) {
            getAttributes() >> otherVariantAttributes
            asDescribable() >> Describables.of("mock other resolved variant")
        }
        def yetAnotherVariant = Mock(ResolvedVariant) {
            getAttributes() >> yetAnotherVariantAttributes
            asDescribable() >> Describables.of("mock another resolved variant")
        }
        def multiVariantSet = Mock(ResolvedVariantSet) {
            asDescribable() >> Describables.of("mock multi producer")
            getVariants() >> [variant, otherVariant, yetAnotherVariant]
        }
        def transform1 = Mock(TransformationStep)
        def transform2 = Mock(TransformationStep)
        def transform3 = Mock(TransformationStep)
        def selector = new AttributeMatchingVariantSelector(consumerProvidedVariantFinder, attributesSchema, attributesFactory, transformedVariantFactory, requestedAttributes, false, dependenciesResolverFactory)

        when:
        def result = selector.select(multiVariantSet, factory)

        then:
        result instanceof BrokenResolvedArtifactSet
        result.failure instanceof AmbiguousTransformException

        1 * attributeMatcher.matches(_, _, _) >> Collections.emptyList()
        1 * consumerProvidedVariantFinder.collectConsumerVariants(variantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform1, 3)
        }
        1 * consumerProvidedVariantFinder.collectConsumerVariants(otherVariantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform2, 2)
        }
        1 * consumerProvidedVariantFinder.collectConsumerVariants(yetAnotherVariantAttributes, requestedAttributes) >> { args ->
            match(requestedAttributes, transform3, 3)
        }
        1 * attributeMatcher.matches(_, _, _) >> { args -> args[0] }
        3 * attributeMatcher.isMatching(requestedAttributes, requestedAttributes) >>> [false, false, true]
    }

    static MutableConsumerVariantMatchResult match(ImmutableAttributes output, TransformationStep trn, int depth) {
        def result = new MutableConsumerVariantMatchResult(2)
        result.matched(output, trn, null, depth)
        result
    }
}
