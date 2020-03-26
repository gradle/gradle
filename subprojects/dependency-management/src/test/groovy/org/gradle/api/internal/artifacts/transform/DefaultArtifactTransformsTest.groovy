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
import org.gradle.api.Buildable
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.DefaultMutableAttributeContainer
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.internal.Describables
import org.gradle.internal.Try
import org.gradle.internal.component.AmbiguousVariantSelectionException
import org.gradle.internal.component.NoMatchingVariantSelectionException
import org.gradle.internal.component.model.AttributeMatcher
import org.gradle.internal.component.model.AttributeMatchingExplanationBuilder
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.util.AttributeTestUtil
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.ArtifactAttributes.ARTIFACT_FORMAT
import static org.gradle.util.TextUtil.toPlatformLineSeparators

class DefaultArtifactTransformsTest extends Specification {
    def matchingCache = Mock(ConsumerProvidedVariantFinder)
    def producerSchema = Mock(AttributesSchemaInternal)
    def consumerSchema = Mock(AttributesSchemaInternal) {
        getConsumerDescribers() >> []
    }
    def attributeMatcher = Mock(AttributeMatcher)
    def dependenciesResolver = Stub(ExtraExecutionGraphDependenciesResolverFactory)
    def transformationNodeRegistry = Mock(TransformationNodeRegistry)
    def transforms = new DefaultArtifactTransforms(matchingCache, consumerSchema, AttributeTestUtil.attributesFactory(), transformationNodeRegistry)

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
        attributeMatcher.matches(variants, typeAttributes("classes"), _ as AttributeMatchingExplanationBuilder) >> [variant1]

        expect:
        def result = transforms.variantSelector(typeAttributes("classes"), true, dependenciesResolver).select(set)
        result == variant1Artifacts
    }

    def "fails when multiple producer variants match"() {
        def variant1 = Stub(ResolvedVariant)
        def variant2 = Stub(ResolvedVariant)
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
        attributeMatcher.matches(variants, typeAttributes("classes"), _ as AttributeMatchingExplanationBuilder) >> [variant1, variant2]
        attributeMatcher.isMatching(_, _, _) >> true

        when:
        def result = transforms.variantSelector(typeAttributes("classes"), true, dependenciesResolver).select(set)
        visit(result)

        then:
        def e = thrown(AmbiguousVariantSelectionException)
        e.message == toPlatformLineSeparators("""The consumer was configured to find attribute 'artifactType' with value 'classes'. However we cannot choose between the following variants of <component>:
  - <variant1>:
      - Compatible attribute:
          - Required artifactType 'classes' and found compatible value 'classes'.
  - <variant2>:
      - Compatible attribute:
          - Required artifactType 'classes' and found compatible value 'jar'.""")
    }

    private ResolvedVariant resolvedVariant() {
        Stub(ResolvedVariant)
    }

    private ResolvedVariantSet resolvedVariantSet() {
        Stub(ResolvedVariantSet) {
            getOverriddenAttributes() >> ImmutableAttributes.EMPTY
        }
    }

    def "selects variant with attributes that can be transformed to requested format"() {
        def variant1 = resolvedVariant()
        def variant2 = resolvedVariant()
        def variant1Artifacts = Stub(ResolvedArtifactSet)
        def sourceArtifactId = Stub(ComponentArtifactIdentifier)
        def sourceArtifact = Stub(TestArtifact)
        def sourceArtifactFile = new File("thing-1.0.jar")
        def outFile1 = new File("out1.classes")
        def outFile2 = new File("out2.classes")
        def set = resolvedVariantSet()
        def variants = [variant1, variant2] as Set
        def transformation = Mock(Transformation)
        CacheableInvocation<TransformationSubject> invocation1 = Mock(CacheableInvocation)
        def listener = Mock(ResolvedArtifactSet.AsyncArtifactListener)
        def visitor = Mock(ArtifactVisitor)
        def targetAttributes = typeAttributes("classes")
        def variant1DisplayName = Describables.of('variant1')

        given:
        sourceArtifact.id >> sourceArtifactId
        set.schema >> producerSchema
        set.variants >> variants
        variant1.attributes >> typeAttributes("jar")
        variant1.artifacts >> variant1Artifacts
        variant2.attributes >> typeAttributes("dll")

        consumerSchema.withProducer(producerSchema) >> attributeMatcher
        attributeMatcher.matches(_, _, _) >> []

        matchingCache.collectConsumerVariants(typeAttributes("jar"), targetAttributes) >> { AttributeContainerInternal from, AttributeContainerInternal to ->
            match(to, transformation, 1)
        }
        matchingCache.collectConsumerVariants(typeAttributes("dll"), targetAttributes) >> { new ConsumerVariantMatchResult(0) }
        def result = transforms.variantSelector(targetAttributes, true, dependenciesResolver).select(set)

        when:
        result.startVisit(new TestBuildOperationExecutor.TestBuildOperationQueue<RunnableBuildOperation>(), listener).visit(visitor)

        then:

        _ * variant1Artifacts.startVisit(_, _) >> { BuildOperationQueue q, ResolvedArtifactSet.AsyncArtifactListener l ->
            l.artifactAvailable(sourceArtifact)
            return new ResolvedArtifactSet.Completion() {
                @Override
                void visit(ArtifactVisitor v) {
                    v.visitArtifact(variant1DisplayName, targetAttributes, sourceArtifact)
                }
            }
        }
        _ * transformation.getDisplayName() >> "transform"
        _ * transformation.requiresDependencies() >> false
        _ * transformationNodeRegistry.getIfExecuted(_, _) >> Optional.empty()

        1 * transformation.createInvocation({ it.files == [sourceArtifactFile]}, _ as ExecutionGraphDependenciesResolver, _) >> invocation1
        1 * invocation1.getCachedResult() >> Optional.empty()
        1 * invocation1.invoke() >> Try.successful(TransformationSubject.initial(sourceArtifactId, sourceArtifactFile).createSubjectFromResult(ImmutableList.of(outFile1, outFile2))) >> invocation1

        1 * listener.prepareForVisit({it instanceof ConsumerProvidedVariantFiles}) >> FileCollectionStructureVisitor.VisitType.Visit
        1 * visitor.visitArtifact(variant1DisplayName, targetAttributes, {it.file == outFile1})
        1 * visitor.visitArtifact(variant1DisplayName, targetAttributes, {it.file == outFile2})
        1 * visitor.endVisitCollection(FileCollectionInternal.OTHER)
        0 * visitor._
        0 * transformation._
    }

    def "fails when multiple transforms match"() {
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

        matchingCache.collectConsumerVariants(_, _) >> { AttributeContainerInternal from, AttributeContainerInternal to ->
                match(to, Stub(Transformation), 1)
        }

        def selector = transforms.variantSelector(typeAttributes("dll"), true, dependenciesResolver)

        when:
        def result = selector.select(set)
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

        matchingCache.collectConsumerVariants(_, _) >> new ConsumerVariantMatchResult(0)

        expect:
        def result = transforms.variantSelector(typeAttributes("dll"), true, dependenciesResolver).select(set)
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

        matchingCache.collectConsumerVariants(_, _) >> new ConsumerVariantMatchResult(0)

        when:
        def result = transforms.variantSelector(typeAttributes("dll"), false, dependenciesResolver).select(set)
        visit(result)

        then:
        def e = thrown(NoMatchingVariantSelectionException)
        e.message == toPlatformLineSeparators("""No variants of <component> match the consumer attributes:
  - <variant1>:
      - Incompatible attribute:
          - Required artifactType 'dll' and found incompatible value 'jar'.
  - <variant2>:
      - Incompatible attribute:
          - Required artifactType 'dll' and found incompatible value 'classes'.""")
    }

    def visit(ResolvedArtifactSet set) {
        def visitor = Stub(ArtifactVisitor)
        _ * visitor.visitFailure(_) >> { Throwable t -> throw t }
        set.startVisit(new TestBuildOperationExecutor.TestBuildOperationQueue<RunnableBuildOperation>(), Stub(ResolvedArtifactSet.AsyncArtifactListener)).visit(visitor)
    }

    private static AttributeContainerInternal typeAttributes(String artifactType) {
        def attributeContainer = new DefaultMutableAttributeContainer(AttributeTestUtil.attributesFactory())
        attributeContainer.attribute(ARTIFACT_FORMAT, artifactType)
        attributeContainer.asImmutable()
    }

    static ConsumerVariantMatchResult match(ImmutableAttributes output, Transformation trn, int depth) {
        def result = new ConsumerVariantMatchResult(2)
        result.matched(output, trn, depth)
        result
    }

    interface TestArtifact extends ResolvableArtifact, Buildable {}
}
