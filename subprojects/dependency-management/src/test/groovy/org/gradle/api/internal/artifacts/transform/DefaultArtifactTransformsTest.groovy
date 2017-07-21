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

import org.gradle.api.Buildable
import org.gradle.api.Transformer
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory
import org.gradle.api.internal.attributes.DefaultMutableAttributeContainer
import org.gradle.internal.Describables
import org.gradle.internal.component.AmbiguousVariantSelectionException
import org.gradle.internal.component.NoMatchingVariantSelectionException
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier
import org.gradle.internal.component.model.AttributeMatcher
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.operations.TestBuildOperationExecutor
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.ArtifactAttributes.ARTIFACT_FORMAT
import static org.gradle.util.TextUtil.toPlatformLineSeparators

class DefaultArtifactTransformsTest extends Specification {
    def matchingCache = Mock(VariantAttributeMatchingCache)
    def producerSchema = Mock(AttributesSchemaInternal)
    def consumerSchema = Mock(AttributesSchemaInternal)
    def attributeMatcher = Mock(AttributeMatcher)
    def transforms = new DefaultArtifactTransforms(matchingCache, consumerSchema)

    def "selects producer variant with requested attributes"() {
        def variant1 = Stub(ResolvedVariant)
        def variant2 = Stub(ResolvedVariant)
        def variant1Artifacts = Stub(ResolvedArtifactSet)
        def set = Stub(ResolvedVariantSet)
        def variants = [variant1, variant2] as Set

        given:
        set.schema >> producerSchema
        set.variants >> variants
        variant1.attributes >> typeAttributes("classes")
        variant1.artifacts >> variant1Artifacts
        variant2.attributes >> typeAttributes("jar")

        consumerSchema.withProducer(producerSchema) >> attributeMatcher
        attributeMatcher.matches(variants, typeAttributes("classes")) >> [variant1]

        expect:
        def result = transforms.variantSelector(typeAttributes("classes"), true).select(set)
        result == variant1Artifacts
    }

    def "fails when multiple producer variants match"() {
        def variant1 = Stub(ResolvedVariant)
        def variant2 = Stub(ResolvedVariant)
        def set = Stub(ResolvedVariantSet)
        def variants = [variant1, variant2] as Set

        given:
        set.asDescribable() >> Describables.of('<component>')
        set.schema >> producerSchema
        set.variants >> variants
        set.componentIdentifier >> Stub(ModuleComponentIdentifier)
        variant1.asDescribable() >> Describables.of('<variant1>')
        variant1.attributes >> typeAttributes("classes")
        variant2.asDescribable() >> Describables.of('<variant2>')
        variant2.attributes >> typeAttributes("jar")

        consumerSchema.withProducer(producerSchema) >> attributeMatcher
        attributeMatcher.matches(variants, typeAttributes("classes")) >> [variant1, variant2]

        when:
        def result = transforms.variantSelector(typeAttributes("classes"), true).select(set)
        visit(result)

        then:
        def e = thrown(AmbiguousVariantSelectionException)
        e.message == toPlatformLineSeparators("""More than one variant of <component> matches the consumer attributes:
  - <variant1>: Required artifactType 'classes' and found incompatible value 'classes'.
  - <variant2>: Required artifactType 'classes' and found incompatible value 'jar'.""")
    }

    def "selects variant with attributes that can be transformed to requested format"() {
        def variant1 = Stub(ResolvedVariant)
        def variant2 = Stub(ResolvedVariant)
        def variant1Artifacts = Stub(ResolvedArtifactSet)
        def id = Stub(ComponentIdentifier)
        def sourceArtifact = Stub(TestArtifact)
        def sourceArtifactFile = new File("thing-1.0.jar")
        def sourceFile = new File("thing-file.jar")
        def outFile1 = new File("out1.classes")
        def outFile2 = new File("out2.classes")
        def outFile3 = new File("out3.classes")
        def outFile4 = new File("out4.classes")
        def set = Stub(ResolvedVariantSet)
        def variants = [variant1, variant2] as Set
        def transformer = Mock(Transformer)
        def listener = Mock(ResolvedArtifactSet.AsyncArtifactListener)
        def visitor = Mock(ArtifactVisitor)
        def targetAttributes = typeAttributes("classes")

        given:
        set.schema >> producerSchema
        set.variants >> variants
        set.componentIdentifier >> Stub(ModuleComponentIdentifier)
        variant1.attributes >> typeAttributes("jar")
        variant1.artifacts >> variant1Artifacts
        variant2.attributes >> typeAttributes("dll")

        consumerSchema.withProducer(producerSchema) >> attributeMatcher
        attributeMatcher.matches(_, _) >> []

        matchingCache.collectConsumerVariants(typeAttributes("jar"), targetAttributes, _) >> { AttributeContainerInternal from, AttributeContainerInternal to, ConsumerVariantMatchResult result ->
            result.matched(to, transformer, 1)
        }
        matchingCache.collectConsumerVariants(typeAttributes("dll"), targetAttributes, _) >> { }

        def result = transforms.variantSelector(targetAttributes, true).select(set)

        when:
        result.startVisit(new TestBuildOperationExecutor.TestBuildOperationQueue<RunnableBuildOperation>(), listener).visit(visitor)

        then:
        _ * variant1Artifacts.startVisit(_, _) >> { BuildOperationQueue q, ResolvedArtifactSet.AsyncArtifactListener l ->
            l.artifactAvailable(sourceArtifact)
            l.fileAvailable(sourceFile)
            return new ResolvedArtifactSet.Completion() {
                @Override
                void visit(ArtifactVisitor v) {
                    v.visitArtifact(targetAttributes, sourceArtifact)
                    v.visitFile(new ComponentFileArtifactIdentifier(id, sourceFile.name), targetAttributes, sourceFile)
                }
            }
        }
        1 * transformer.transform(sourceArtifactFile) >> [outFile1, outFile2]
        1 * transformer.transform(sourceFile) >> [outFile3, outFile4]
        1 * visitor.visitArtifact(targetAttributes, {it.file == outFile1})
        1 * visitor.visitArtifact(targetAttributes, {it.file == outFile2})
        1 * visitor.visitFile(new ComponentFileArtifactIdentifier(id, outFile3.name), targetAttributes, outFile3)
        1 * visitor.visitFile(new ComponentFileArtifactIdentifier(id, outFile4.name), targetAttributes, outFile4)
        0 * visitor._
        0 * transformer._
    }

    def "fails when multiple transforms match"() {
        def variant1 = Stub(ResolvedVariant)
        def variant2 = Stub(ResolvedVariant)
        def set = Stub(ResolvedVariantSet)
        def variants = [variant1, variant2] as Set

        given:
        set.schema >> producerSchema
        set.variants >> variants
        set.asDescribable() >> Describables.of('<component>')
        set.componentIdentifier >> Stub(ModuleComponentIdentifier)
        variant1.attributes >> typeAttributes("jar")
        variant1.asDescribable() >> Describables.of('<variant1>')
        variant2.attributes >> typeAttributes("classes")
        variant2.asDescribable() >> Describables.of('<variant2>')

        consumerSchema.withProducer(producerSchema) >> attributeMatcher
        attributeMatcher.matches(_, _) >> []

        matchingCache.collectConsumerVariants(_, _, _) >> { AttributeContainerInternal from, AttributeContainerInternal to, ConsumerVariantMatchResult result ->
                result.matched(to, Stub(Transformer), 1)
        }

        def selector = transforms.variantSelector(typeAttributes("dll"), true)

        when:
        def result = selector.select(set)
        visit(result)

        then:
        def e = thrown(AmbiguousTransformException)
        e.message == toPlatformLineSeparators("""Found multiple transforms that can produce a variant of <component> for consumer attributes: artifactType 'dll'
Found the following transforms:
  - Transform from <variant1>: artifactType 'jar'
  - Transform from <variant2>: artifactType 'classes'""")
    }

    def "returns empty variant when no variants match and ignore no matching enabled"() {
        def variant1 = Stub(ResolvedVariant)
        def variant2 = Stub(ResolvedVariant)
        def set = Stub(ResolvedVariantSet)
        def variants = [variant1, variant2] as Set

        given:
        set.schema >> producerSchema
        set.variants >> variants
        variant1.attributes >> typeAttributes("jar")
        variant2.attributes >> typeAttributes("classes")

        consumerSchema.withProducer(producerSchema) >> attributeMatcher
        attributeMatcher.matches(_, _) >> []

        matchingCache.collectConsumerVariants(typeAttributes("dll"), typeAttributes("jar"), _) >> null
        matchingCache.collectConsumerVariants(typeAttributes("dll"), typeAttributes("classes"), _) >> null

        expect:
        def result = transforms.variantSelector(typeAttributes("dll"), true).select(set)
        result == ResolvedArtifactSet.EMPTY
    }

    def "fails when no variants match and ignore no matching disabled"() {
        def variant1 = Stub(ResolvedVariant)
        def variant2 = Stub(ResolvedVariant)
        def set = Stub(ResolvedVariantSet)
        def variants = [variant1, variant2] as Set

        given:
        set.schema >> producerSchema
        set.variants >> variants
        set.asDescribable() >> Describables.of('<component>')
        set.componentIdentifier >> Stub(ModuleComponentIdentifier)
        variant1.attributes >> typeAttributes("jar")
        variant1.asDescribable() >> Describables.of('<variant1>')
        variant2.attributes >> typeAttributes("classes")
        variant2.asDescribable() >> Describables.of('<variant2>')

        consumerSchema.withProducer(producerSchema) >> attributeMatcher
        attributeMatcher.matches(_, _) >> []

        matchingCache.collectConsumerVariants(typeAttributes("dll"), typeAttributes("jar"), _) >> null
        matchingCache.collectConsumerVariants(typeAttributes("dll"), typeAttributes("classes"), _) >> null

        when:
        def result = transforms.variantSelector(typeAttributes("dll"), false).select(set)
        visit(result)

        then:
        def e = thrown(NoMatchingVariantSelectionException)
        e.message == toPlatformLineSeparators("""No variants of <component> match the consumer attributes:
  - <variant1>: Required artifactType 'dll' and found incompatible value 'jar'.
  - <variant2>: Required artifactType 'dll' and found incompatible value 'classes'.""")
    }

    def visit(ResolvedArtifactSet set) {
        def visitor = Stub(ArtifactVisitor)
        _ * visitor.visitFailure(_) >> { Throwable t -> throw t }
        set.startVisit(new TestBuildOperationExecutor.TestBuildOperationQueue<RunnableBuildOperation>(), Stub(ResolvedArtifactSet.AsyncArtifactListener)).visit(visitor)
    }

    private AttributeContainerInternal typeAttributes(String artifactType) {
        def attributeContainer = new DefaultMutableAttributeContainer(new DefaultImmutableAttributesFactory())
        attributeContainer.attribute(ARTIFACT_FORMAT, artifactType)
        attributeContainer.asImmutable()
    }

    interface TestArtifact extends ResolvableArtifact, Buildable {}
}
