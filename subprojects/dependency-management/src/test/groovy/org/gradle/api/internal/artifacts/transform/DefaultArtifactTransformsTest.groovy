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
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.EmptyResolvedVariant
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory
import org.gradle.api.internal.attributes.DefaultMutableAttributeContainer
import org.gradle.internal.component.AmbiguousVariantSelectionException
import org.gradle.internal.component.NoMatchingVariantSelectionException
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier
import org.gradle.internal.component.model.AttributeMatcher
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

        given:
        variant1.attributes >> typeAttributes("classes")
        variant2.attributes >> typeAttributes("jar")

        consumerSchema.withProducer(producerSchema) >> attributeMatcher
        attributeMatcher.matches([variant1, variant2], typeAttributes("classes")) >> [variant1]

        expect:
        def result = transforms.variantSelector(typeAttributes("classes"), true).select([variant1, variant2], producerSchema)
        result == variant1
    }

    def "fails when multiple producer variants match"() {
        def variant1 = Stub(ResolvedVariant)
        def variant2 = Stub(ResolvedVariant)

        given:
        variant1.attributes >> typeAttributes("classes")
        variant2.attributes >> typeAttributes("jar")

        consumerSchema.withProducer(producerSchema) >> attributeMatcher
        attributeMatcher.matches([variant1, variant2], typeAttributes("classes")) >> [variant1, variant2]

        when:
        transforms.variantSelector(typeAttributes("classes"), true).select([variant1, variant2], producerSchema)

        then:
        def e = thrown(AmbiguousVariantSelectionException)
        e.message == toPlatformLineSeparators("""More than one variant matches the consumer attributes:
  - Variant: Required artifactType 'classes' and found incompatible value 'classes'.
  - Variant: Required artifactType 'classes' and found incompatible value 'jar'.""")
    }

    def "selects variant with attributes that can be transformed to requested format"() {
        def variant1 = Stub(ResolvedVariant)
        def variant2 = Stub(ResolvedVariant)
        def id = Stub(ComponentIdentifier)
        def sourceArtifact = Stub(TestArtifact)
        def sourceArtifactFile = new File("thing-1.0.jar")
        def sourceFile = new File("thing-file.jar")
        def outFile1 = new File("out1.classes")
        def outFile2 = new File("out2.classes")
        def outFile3 = new File("out3.classes")
        def outFile4 = new File("out4.classes")
        def transformer = Mock(Transformer)
        def visitor = Mock(ArtifactVisitor)
        def targetAttributes = typeAttributes("classes")

        given:
        variant1.attributes >> typeAttributes("jar")
        variant2.attributes >> typeAttributes("dll")

        consumerSchema.withProducer(producerSchema) >> attributeMatcher
        attributeMatcher.matches(_, _) >> []

        matchingCache.collectConsumerVariants(typeAttributes("jar"), targetAttributes, _) >> { AttributeContainerInternal from, AttributeContainerInternal to, ConsumerVariantMatchResult result ->
            result.matched(to, transformer, 1)
        }
        matchingCache.collectConsumerVariants(typeAttributes("dll"), targetAttributes, _) >> { }

        when:
        def result = transforms.variantSelector(targetAttributes, true).select([variant1, variant2], producerSchema)
        result.visit(visitor)

        then:
        _ * variant1.visit(_) >> { ArtifactVisitor v ->
            v.visitArtifact(targetAttributes, sourceArtifact)
            v.visitFile(new ComponentFileArtifactIdentifier(id, sourceFile.name), targetAttributes, sourceFile)
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

        given:
        variant1.attributes >> typeAttributes("jar")
        variant2.attributes >> typeAttributes("classes")

        consumerSchema.withProducer(producerSchema) >> attributeMatcher
        attributeMatcher.matches(_, _) >> []

        matchingCache.collectConsumerVariants(_, _, _) >> { AttributeContainerInternal from, AttributeContainerInternal to, ConsumerVariantMatchResult result ->
                result.matched(to, Stub(Transformer), 1)
        }

        def selector = transforms.variantSelector(typeAttributes("dll"), true)

        when:
        selector.select([variant1, variant2], producerSchema)

        then:
        def e = thrown(AmbiguousTransformException)
        e.message == toPlatformLineSeparators("""Found multiple transforms that can produce a variant for consumer attributes: artifactType 'dll'
Found the following transforms:
  - Transform from variant: artifactType 'jar'
  - Transform from variant: artifactType 'classes'""")
    }

    def "returns empty variant when no variants match and ignore no matching enabled"() {
        def variant1 = Stub(ResolvedVariant)
        def variant2 = Stub(ResolvedVariant)

        given:
        variant1.attributes >> typeAttributes("jar")
        variant2.attributes >> typeAttributes("classes")

        consumerSchema.withProducer(producerSchema) >> attributeMatcher
        attributeMatcher.matches(_, _) >> []

        matchingCache.collectConsumerVariants(typeAttributes("dll"), typeAttributes("jar"), _) >> null
        matchingCache.collectConsumerVariants(typeAttributes("dll"), typeAttributes("classes"), _) >> null

        expect:
        def result = transforms.variantSelector(typeAttributes("dll"), true).select([variant1, variant2], producerSchema)
        result instanceof EmptyResolvedVariant
    }

    def "fails when no variants match and ignore no matching disabled"() {
        def variant1 = Stub(ResolvedVariant)
        def variant2 = Stub(ResolvedVariant)

        given:
        variant1.attributes >> typeAttributes("jar")
        variant2.attributes >> typeAttributes("classes")

        consumerSchema.withProducer(producerSchema) >> attributeMatcher
        attributeMatcher.matches(_, _) >> []

        matchingCache.collectConsumerVariants(typeAttributes("dll"), typeAttributes("jar"), _) >> null
        matchingCache.collectConsumerVariants(typeAttributes("dll"), typeAttributes("classes"), _) >> null

        when:
        transforms.variantSelector(typeAttributes("dll"), false).select([variant1, variant2], producerSchema)

        then:
        def e = thrown(NoMatchingVariantSelectionException)
        e.message == toPlatformLineSeparators("""No variants match the consumer attributes:
  - Variant: Required artifactType 'dll' and found incompatible value 'jar'.
  - Variant: Required artifactType 'dll' and found incompatible value 'classes'.""")
    }

    private AttributeContainerInternal typeAttributes(String artifactType) {
        def attributeContainer = new DefaultMutableAttributeContainer(new DefaultImmutableAttributesFactory())
        attributeContainer.attribute(ARTIFACT_FORMAT, artifactType)
        attributeContainer.asImmutable()
    }

    interface TestArtifact extends ResolvedArtifact, Buildable {}
}
