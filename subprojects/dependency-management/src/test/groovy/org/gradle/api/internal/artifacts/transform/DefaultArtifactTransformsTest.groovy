/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableList
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.DefaultMutableAttributeContainer
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.Describables
import org.gradle.internal.component.AmbiguousVariantSelectionException
import org.gradle.internal.component.NoMatchingVariantSelectionException
import org.gradle.internal.component.model.AttributeMatcher
import org.gradle.internal.component.model.AttributeMatchingExplanationBuilder
import org.gradle.util.AttributeTestUtil
import spock.lang.Specification

import static org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class DefaultArtifactTransformsTest extends Specification {
    def matchingCache = Mock(ConsumerProvidedVariantFinder)
    def producerSchema = Mock(AttributesSchemaInternal)
    def consumerSchema = Mock(AttributesSchemaInternal) {
        getConsumerDescribers() >> []
    }
    def attributeMatcher = Mock(AttributeMatcher)
    def factory = Mock(VariantSelector.Factory)
    def dependenciesResolver = Stub(ExtraExecutionGraphDependenciesResolverFactory)
    def transformedVariantFactory = Mock(TransformedVariantFactory)
    def transforms = new DefaultArtifactTransforms(matchingCache, consumerSchema, AttributeTestUtil.attributesFactory(), transformedVariantFactory)

    def "selects producer variant with requested attributes"() {
        def variant1 = resolvedVariant()
        def variant2 = resolvedVariant()
        def variant1Artifacts = Stub(ResolvedArtifactSet)
        def set = resolvedVariantSet()
        def variants = [variant1, variant2] as Set

        given:
        set.schema >> producerSchema
        set.variants >> variants
        variant1.attributes >> typeAttributes("classes")
        variant1.artifacts >> variant1Artifacts
        variant2.attributes >> typeAttributes("jar")

        consumerSchema.withProducer(producerSchema) >> attributeMatcher
        attributeMatcher.matches(_ as Collection, typeAttributes("classes"), _ as AttributeMatchingExplanationBuilder) >> [variant1]

        expect:
        def result = transforms.variantSelector(typeAttributes("classes"), true, false, dependenciesResolver).select(set, factory)
        result == variant1Artifacts
    }

    def "fails when multiple producer variants match"() {
        def variant1 = resolvedVariant()
        def variant2 = resolvedVariant()
        def set = resolvedVariantSet()
        def variants = [variant1, variant2] as Set

        given:
        set.asDescribable() >> Describables.of('<component>')
        set.schema >> producerSchema
        set.variants >> variants
        variant1.asDescribable() >> Describables.of('<variant1>')
        variant1.attributes >> typeAttributes("classes")
        variant2.asDescribable() >> Describables.of('<variant2>')
        variant2.attributes >> typeAttributes("jar")

        consumerSchema.withProducer(producerSchema) >> attributeMatcher
        attributeMatcher.matches(_ as Collection, typeAttributes("classes"), _ as AttributeMatchingExplanationBuilder) >> [variant1, variant2]
        attributeMatcher.isMatching(_, _, _) >> true

        when:
        def result = transforms.variantSelector(typeAttributes("classes"), true, false, dependenciesResolver).select(set, factory)
        visit(result)

        then:
        def e = thrown(AmbiguousVariantSelectionException)
        e.message == toPlatformLineSeparators("""The consumer was configured to find attribute 'artifactType' with value 'classes'. However we cannot choose between the following variants of <component>:
  - <variant1> declares attribute 'artifactType' with value 'classes'
  - <variant2> declares attribute 'artifactType' with value 'jar'""")
    }

    def "fails when multiple transforms match"() {
        def requested = typeAttributes("dll")
        def variant1 = resolvedVariant()
        def variant2 = resolvedVariant()
        def set = resolvedVariantSet()
        def variants = [variant1, variant2] as Set
        def transformedVariants = variants.collect { transformedVariant(it, requested)}

        given:
        set.schema >> producerSchema
        set.variants >> variants
        set.asDescribable() >> Describables.of('<component>')
        variant1.attributes >> typeAttributes("jar")
        variant1.asDescribable() >> Describables.of('<variant1>')
        variant2.attributes >> typeAttributes("classes")
        variant2.asDescribable() >> Describables.of('<variant2>')

        consumerSchema.withProducer(producerSchema) >> attributeMatcher
        attributeMatcher.matches(ImmutableList.copyOf(variants), _, _) >> []
        attributeMatcher.matches(transformedVariants, _, _) >> transformedVariants
        matchingCache.findTransformedVariants(_, _) >> transformedVariants

        def selector = transforms.variantSelector(requested, true, false, dependenciesResolver)

        when:
        def result = selector.select(set, factory)
        visit(result)

        then:
        def e = thrown(AmbiguousTransformException)
        e.message == toPlatformLineSeparators("""Found multiple transforms that can produce a variant of <component> with requested attributes:
  - artifactType 'dll'
Found the following transforms:
  - From '<variant1>':
      - With source attributes: artifactType 'jar'
      - Candidate transform(s):
          - Transform '' producing attributes: artifactType 'dll'
  - From '<variant2>':
      - With source attributes: artifactType 'classes'
      - Candidate transform(s):
          - Transform '' producing attributes: artifactType 'dll'""")
    }

    def "returns empty variant when no variants match and ignore no matching enabled"() {
        def variant1 = resolvedVariant()
        def variant2 = resolvedVariant()
        def set = resolvedVariantSet()
        def variants = [variant1, variant2] as Set

        given:
        set.schema >> producerSchema
        set.variants >> variants
        variant1.attributes >> typeAttributes("jar")
        variant2.attributes >> typeAttributes("classes")

        consumerSchema.withProducer(producerSchema) >> attributeMatcher
        attributeMatcher.matches(_, _, _) >> []

        matchingCache.findTransformedVariants(_, _) >> []

        expect:
        def result = transforms.variantSelector(typeAttributes("dll"), true, false, dependenciesResolver).select(set, factory)
        result == ResolvedArtifactSet.EMPTY
    }

    def "fails when no variants match and ignore no matching disabled"() {
        def variant1 = resolvedVariant()
        def variant2 = resolvedVariant()
        def set = resolvedVariantSet()
        def variants = [variant1, variant2] as Set

        given:
        set.schema >> producerSchema
        set.variants >> variants
        set.asDescribable() >> Describables.of('<component>')
        variant1.attributes >> typeAttributes("jar")
        variant1.asDescribable() >> Describables.of('<variant1>')
        variant2.attributes >> typeAttributes("classes")
        variant2.asDescribable() >> Describables.of('<variant2>')

        consumerSchema.withProducer(producerSchema) >> attributeMatcher
        attributeMatcher.matches(_, _, _) >> []

        matchingCache.findTransformedVariants(_, _) >> []

        when:
        def result = transforms.variantSelector(typeAttributes("dll"), false, false, dependenciesResolver).select(set, factory)
        visit(result)

        then:
        def e = thrown(NoMatchingVariantSelectionException)
        e.message == toPlatformLineSeparators("""No variants of  match the consumer attributes:
  - <variant1>:
      - Incompatible because this component declares attribute 'artifactType' with value 'jar' and the consumer needed attribute 'artifactType' with value 'dll'
  - <variant2>:
      - Incompatible because this component declares attribute 'artifactType' with value 'classes' and the consumer needed attribute 'artifactType' with value 'dll'""")
    }

    private ResolvedVariant resolvedVariant() {
        Stub(ResolvedVariant)
    }

    private ResolvedVariantSet resolvedVariantSet() {
        Stub(ResolvedVariantSet) {
            getOverriddenAttributes() >> ImmutableAttributes.EMPTY
        }
    }

    def visit(ResolvedArtifactSet set) {
        def artifactVisitor = Stub(ArtifactVisitor)
        _ * artifactVisitor.visitFailure(_) >> { Throwable t -> throw t }
        def visitor = Stub(ResolvedArtifactSet.Visitor)
        _ * visitor.visitArtifacts(_) >> { ResolvedArtifactSet.Artifacts artifacts -> artifacts.visit(artifactVisitor) }
        set.visit(visitor)
    }

    private static AttributeContainerInternal typeAttributes(String artifactType) {
        def attributeContainer = new DefaultMutableAttributeContainer(AttributeTestUtil.attributesFactory())
        attributeContainer.attribute(ARTIFACT_TYPE_ATTRIBUTE, artifactType)
        attributeContainer.asImmutable()
    }

    TransformedVariant transformedVariant(ResolvedVariant root, AttributeContainerInternal attributes) {
        ImmutableAttributes attrs = attributes.asImmutable()
        TransformationStep step = Mock(TransformationStep) {
            getDisplayName() >> ""
        }
        VariantDefinition definition = Mock(VariantDefinition) {
            getTransformationChain() >> new TransformationChain(null, step)
            getTargetAttributes() >> attrs
        }
        return new TransformedVariant(root, definition)
    }
}
