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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory
import org.gradle.api.internal.attributes.DefaultMutableAttributeContainer
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.ArtifactAttributes.ARTIFACT_FORMAT
import static org.gradle.util.TextUtil.toPlatformLineSeparators

class DefaultArtifactTransformsTest extends Specification {
    def matchingCache = Mock(VariantAttributeMatchingCache)
    def transforms = new DefaultArtifactTransforms(matchingCache)

    def "selects variant with requested attributes"() {
        def variant1 = Stub(ResolvedVariant)
        def variant2 = Stub(ResolvedVariant)
        def artifacts1 = Stub(ResolvedArtifactSet)

        given:
        variant1.attributes >> typeAttributes("classes")
        variant2.attributes >> typeAttributes("jar")
        variant1.artifacts >> artifacts1

        matchingCache.selectMatches([variant1, variant2], typeAttributes("classes")) >> [variant1]

        expect:
        def result = transforms.variantSelector(typeAttributes("classes")).transform([variant1, variant2])
        result == artifacts1
    }

    def "selects variant with attributes that can be transformed to requested format"() {
        def variant1 = Stub(ResolvedVariant)
        def variant2 = Stub(ResolvedVariant)
        def artifacts1 = Stub(ResolvedArtifactSet)
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
        variant1.artifacts >> artifacts1

        matchingCache.selectMatches(_, _) >> []
        matchingCache.collectConsumerVariants(typeAttributes("jar"), targetAttributes, _) >> { AttributeContainerInternal from, AttributeContainerInternal to, ConsumerVariantMatchResult result ->
            result.matched(to, transformer, 1)
        }
        matchingCache.collectConsumerVariants(typeAttributes("dll"), targetAttributes, _) >> { }

        when:
        def result = transforms.variantSelector(targetAttributes).transform([variant1, variant2])
        result.visit(visitor)

        then:
        _ * artifacts1.visit(_) >> { ArtifactVisitor v ->
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

        matchingCache.selectMatches(_, _) >> []
        matchingCache.collectConsumerVariants(_, _, _) >> { AttributeContainerInternal from, AttributeContainerInternal to, ConsumerVariantMatchResult result ->
                result.matched(to, Stub(Transformer), 1)
        }

        def selector = transforms.variantSelector(typeAttributes("dll"))

        when:
        selector.transform([variant1, variant2])

        then:
        def e = thrown(AmbiguousTransformException)
        e.message == toPlatformLineSeparators("""Found multiple transforms that can produce a variant for consumer attributes: artifactType 'dll'
Found the following transforms:
  - Transform from variant: artifactType 'jar'
  - Transform from variant: artifactType 'classes'""")
    }

    def "selects no variant when none match"() {
        def variant1 = Stub(ResolvedVariant)
        def variant2 = Stub(ResolvedVariant)

        given:
        variant1.attributes >> typeAttributes("jar")
        variant2.attributes >> typeAttributes("classes")

        matchingCache.selectMatches(_, _) >> []
        matchingCache.collectConsumerVariants(typeAttributes("dll"), typeAttributes("jar"), _) >> null
        matchingCache.collectConsumerVariants(typeAttributes("dll"), typeAttributes("classes"), _) >> null

        expect:
        def result = transforms.variantSelector(typeAttributes("dll")).transform([variant1, variant2])
        result == ResolvedArtifactSet.EMPTY
    }

    private AttributeContainerInternal typeAttributes(String artifactType) {
        def attributeContainer = new DefaultMutableAttributeContainer(new DefaultImmutableAttributesFactory())
        attributeContainer.attribute(ARTIFACT_FORMAT, artifactType)
        attributeContainer.asImmutable()
    }

    interface TestArtifact extends ResolvedArtifact, Buildable {}
}
