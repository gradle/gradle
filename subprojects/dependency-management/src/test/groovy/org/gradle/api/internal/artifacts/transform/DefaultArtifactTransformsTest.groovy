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
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.ArtifactAttributes.ARTIFACT_FORMAT

class DefaultArtifactTransformsTest extends Specification {
    def matchingCache = Mock(ArtifactAttributeMatchingCache)
    def immutableAttributesFactory = new DefaultImmutableAttributesFactory()
    def transforms = new DefaultArtifactTransforms(immutableAttributesFactory, matchingCache)

    def "selects variant with requested attributes"() {
        def variant1 = Stub(ResolvedVariant)
        def variant2 = Stub(ResolvedVariant)
        def artifacts1 = Stub(ResolvedArtifactSet)

        given:
        variant1.attributes >> typeAttributes("classes")
        variant2.attributes >> typeAttributes("jar")
        variant1.artifacts >> artifacts1

        matchingCache.areMatchingAttributes(typeAttributes("classes"), typeAttributes("classes")) >> true

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

        matchingCache.getGeneratedVariant(typeAttributes("jar"), targetAttributes) >> new ArtifactAttributeMatchingCache.GeneratedVariant(targetAttributes, transformer)
        matchingCache.getGeneratedVariant(typeAttributes("dll"), targetAttributes) >> null

        when:
        def result = transforms.variantSelector(targetAttributes).transform([variant1, variant2])
        result.visit(visitor)

        then:
        _ * artifacts1.visit(_) >> { ArtifactVisitor v ->
            v.visitArtifact(targetAttributes, sourceArtifact)
            v.visitFiles(id, targetAttributes, [sourceFile])
        }
        1 * transformer.transform(sourceArtifactFile) >> [outFile1, outFile2]
        1 * transformer.transform(sourceFile) >> [outFile3, outFile4]
        1 * visitor.visitArtifact(targetAttributes, {it.file == outFile1})
        1 * visitor.visitArtifact(targetAttributes, {it.file == outFile2})
        1 * visitor.visitFiles(id, targetAttributes, [outFile3, outFile4])
        0 * visitor._
        0 * transformer._
    }

    def "selects variant with requested attributes when another variant can be transformed"() {
        def variant1 = Stub(ResolvedVariant)
        def variant2 = Stub(ResolvedVariant)
        def artifacts2 = Stub(ResolvedArtifactSet)

        given:
        variant1.attributes >> typeAttributes("jar")
        variant2.attributes >> typeAttributes("classes")
        variant2.artifacts >> artifacts2

        matchingCache.getGeneratedVariant(typeAttributes("jar"), typeAttributes("classes")) >> Stub(ArtifactAttributeMatchingCache.GeneratedVariant)
        matchingCache.areMatchingAttributes(typeAttributes("classes"), typeAttributes("classes")) >> true

        expect:
        def result = transforms.variantSelector(typeAttributes("classes")).transform([variant1, variant2])
        result == artifacts2
    }

    def "selects no variant when none match"() {
        def variant1 = Stub(ResolvedVariant)
        def variant2 = Stub(ResolvedVariant)

        given:
        variant1.attributes >> typeAttributes("jar")
        variant2.attributes >> typeAttributes("classes")

        matchingCache.getGeneratedVariant(typeAttributes("dll"), typeAttributes("jar")) >> null
        matchingCache.getGeneratedVariant(typeAttributes("dll"), typeAttributes("classes")) >> null

        expect:
        def result = transforms.variantSelector(typeAttributes("dll")).transform([variant1, variant2])
        result == ResolvedArtifactSet.EMPTY
    }

    private AttributeContainerInternal typeAttributes(String artifactType) {
        def attributeContainer = new DefaultMutableAttributeContainer(immutableAttributesFactory)
        attributeContainer.attribute(ARTIFACT_FORMAT, artifactType)
        attributeContainer.asImmutable()
    }

    interface TestArtifact extends ResolvedArtifact, Buildable {}
}
