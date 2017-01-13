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
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.internal.artifacts.attributes.DefaultArtifactAttributes
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.DefaultMutableAttributeContainer
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributesFactory
import org.gradle.internal.component.model.ComponentAttributeMatcher
import org.gradle.internal.resolve.ArtifactResolveException
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.ArtifactAttributes.ARTIFACT_CLASSIFIER
import static org.gradle.api.internal.artifacts.ArtifactAttributes.ARTIFACT_EXTENSION
import static org.gradle.api.internal.artifacts.ArtifactAttributes.ARTIFACT_FORMAT

class DefaultArtifactTransformsTest extends Specification {
    def matchingCache = Mock(ArtifactAttributeMatchingCache)
    def transformer = new DefaultArtifactTransforms(matchingCache)
    def ImmutableAttributesFactory immutableAttributesFactory

    def setup() {
        immutableAttributesFactory = new DefaultImmutableAttributesFactory()
    }

    def "forwards artifact whose type matches requested format"() {
        def visitor = Mock(ArtifactVisitor)
        def artifact = Stub(ResolvedArtifact)
        def requestAttributes = typeAttributes("classpath")

        given:
        artifact.attributes >> requestAttributes

        when:
        def transformVisitor = transformer.visitor(visitor, requestAttributes, immutableAttributesFactory)
        transformVisitor.visitArtifact(artifact)

        then:
        1 * visitor.visitArtifact(artifact)
        1 * matchingCache.getTransformedArtifacts(artifact, requestAttributes) >> null
        1 * matchingCache.areMatchingAttributes(requestAttributes, requestAttributes) >> true
        1 * matchingCache.putTransformedArtifact(artifact, requestAttributes, [artifact])
        0 * _
    }

    def "applies matching transform to artifact whose type does not matches requested format"() {
        def visitor = Mock(ArtifactVisitor)
        def artifact = Stub(TestArtifact)
        def transform = Mock(Transformer)
        def file = new File("thing.zip")
        def transformedFile = new File("thing.classpath")
        def transformedArtifact = null

        given:
        artifact.attributes >> typeAttributes("zip")

        when:
        def transformVisitor = transformer.visitor(visitor, typeAttributes("classpath"), immutableAttributesFactory)
        transformVisitor.visitArtifact(artifact)

        then:
        1 * matchingCache.areMatchingAttributes(typeAttributes("zip"), typeAttributes("classpath")) >> false
        1 * matchingCache.getTransformedArtifacts(artifact, typeAttributes("classpath"))
        1 * matchingCache.getTransform(typeAttributes("zip"), typeAttributes("classpath")) >> transform
        1 * transform.transform(file) >> [transformedFile]
        1 * matchingCache.putTransformedArtifact(artifact, typeAttributes("classpath"), _)
        1 * visitor.visitArtifact(_) >> { ResolvedArtifact a -> transformedArtifact = a }
        0 * _

        and:
        transformedArtifact.type == "classpath"
        transformedArtifact.file == transformedFile
    }

    def "ignores artifact whose type cannot be transformed"() {
        def visitor = Mock(ArtifactVisitor)
        def artifact = Stub(ResolvedArtifact)

        given:
        artifact.attributes >> typeAttributes("lib")
        matchingCache.getTransform("lib", "classpath") >> null

        when:
        def transformVisitor = transformer.visitor(visitor, typeAttributes("classpath"), immutableAttributesFactory)
        transformVisitor.visitArtifact(artifact)

        then:
        def e = thrown(ArtifactResolveException)
        e.message == "Artifact $artifact is not compatible with requested attributes {artifactType=classpath}"
        0 * visitor._
    }

    def "forwards file whose extension matches requested format"() {
        def visitor = Mock(ArtifactVisitor)
        def id = Stub(ComponentIdentifier)
        def file = new File("thing.classpath")

        def requestAttributes = typeAttributes("classpath")

        when:
        def transformVisitor = transformer.visitor(visitor, requestAttributes, immutableAttributesFactory)
        transformVisitor.visitFiles(id, [file])

        then:
        1 * matchingCache.getTransformedFile(file, requestAttributes) >> null
        1 * matchingCache.areMatchingAttributes(DefaultArtifactAttributes.forFile(file, immutableAttributesFactory), requestAttributes) >> true
        1 * matchingCache.putTransformedFile(file, requestAttributes, [file]) >> null
        1 * visitor.visitFiles(id, [file])
        0 * _
    }

    def "applies transform to file whose extension does not match requested format"() {
        def visitor = Mock(ArtifactVisitor)
        def id = Stub(ComponentIdentifier)
        def transform = Mock(Transformer)
        def file = new File("thing.zip")
        def transformedFile = new File("thing.classpath")

        when:
        def transformVisitor = transformer.visitor(visitor, typeAttributes("classpath"), immutableAttributesFactory)
        transformVisitor.visitFiles(id, [file])

        then:
        1 * matchingCache.getTransformedFile(file, typeAttributes("classpath")) >> null
        1 * matchingCache.areMatchingAttributes(DefaultArtifactAttributes.forFile(file, immutableAttributesFactory), typeAttributes("classpath")) >> false
        1 * matchingCache.getTransform(DefaultArtifactAttributes.forFile(file, immutableAttributesFactory), typeAttributes("classpath")) >> transform
        1 * matchingCache.putTransformedFile(file, typeAttributes("classpath"), [transformedFile])
        1 * transform.transform(file) >> [transformedFile]
        1 * visitor.visitFiles(id, [transformedFile])
        0 * _
    }

    def "ignores file whose extension cannot be transformed"() {
        def visitor = Mock(ArtifactVisitor)
        def id = Stub(ComponentIdentifier)
        def file = new File("thing.lib")

        when:
        def transformVisitor = transformer.visitor(visitor, typeAttributes("classpath"), immutableAttributesFactory)
        transformVisitor.visitFiles(id, [file])

        then:
        1 * matchingCache.getTransformedFile(file, typeAttributes("classpath")) >> null
        1 * matchingCache.areMatchingAttributes(DefaultArtifactAttributes.forFile(file, immutableAttributesFactory), typeAttributes("classpath")) >> false
        1 * matchingCache.getTransform(DefaultArtifactAttributes.forFile(file, immutableAttributesFactory), typeAttributes("classpath")) >> null
        0 * _
    }

    def "applies transform once only"() {
        def visitor = Mock(ArtifactVisitor)
        def id = Stub(ComponentIdentifier)
        def transform = Mock(Transformer)
        def file1 = new File("thing1.zip")
        def transformedFile1 = new File("thing1.classpath")
        def file2 = new File("thing2.zip")
        def transformedFile2 = new File("thing2.classpath")

        given:
        matchingCache.getTransform(DefaultArtifactAttributes.forFile(file1, immutableAttributesFactory), typeAttributes("classpath")) >> transform
        matchingCache.getTransform(DefaultArtifactAttributes.forFile(file2, immutableAttributesFactory), typeAttributes("classpath")) >> transform
        transform.transform(file1) >> [transformedFile1]

        def transformVisitor = transformer.visitor(visitor, typeAttributes("classpath"), immutableAttributesFactory)
        transformVisitor.visitFiles(id, [file1])

        when:
        transformVisitor.visitFiles(id, [file1])

        then:
        1 * matchingCache.getTransformedFile(file1, typeAttributes("classpath")) >> [transformedFile1]
        1 * visitor.visitFiles(id, [transformedFile1])
        0 * _

        when:
        transformer.visitor(visitor, typeAttributes("classpath"), immutableAttributesFactory).visitFiles(id, [file1])

        then:
        1 * matchingCache.getTransformedFile(file1, typeAttributes("classpath")) >> [transformedFile1]
        1 * visitor.visitFiles(id, [transformedFile1])
        0 * _

        when:
        transformer.visitor(visitor, typeAttributes("classpath"), immutableAttributesFactory).visitFiles(id, [file1, file2])

        then:
        1 * matchingCache.getTransformedFile(file1, typeAttributes("classpath")) >> [transformedFile1]
        1 * matchingCache.getTransformedFile(file2, typeAttributes("classpath")) >> null
        1 * matchingCache.areMatchingAttributes(DefaultArtifactAttributes.forFile(file2, immutableAttributesFactory), typeAttributes("classpath")) >> false
        1 * matchingCache.getTransform(DefaultArtifactAttributes.forFile(file2, immutableAttributesFactory), typeAttributes("classpath")) >> transform
        1 * transform.transform(file2) >> [transformedFile2]
        1 * matchingCache.putTransformedFile(file2, typeAttributes("classpath"), [transformedFile2])
        1 * visitor.visitFiles(id, [transformedFile1, transformedFile2])
        0 * _

        when:
        transformer.visitor(visitor, typeAttributes("classpath"), immutableAttributesFactory).visitFiles(id, [file1, file2])

        then:
        1 * matchingCache.getTransformedFile(file1, typeAttributes("classpath")) >> [transformedFile1]
        1 * matchingCache.getTransformedFile(file2, typeAttributes("classpath")) >> [transformedFile2]
        1 * visitor.visitFiles(id, [transformedFile1, transformedFile2])
        0 * _
    }

    def "selects variant with requested attributes"() {
        def realCache = new ArtifactAttributeMatchingCache(new DefaultArtifactTransformRegistrations(), setupSchema())
        transformer = new DefaultArtifactTransforms(realCache)
        def artifact1 = Stub(ResolvedArtifact)
        def artifact2 = Stub(ResolvedArtifact)

        given:
        artifact1.attributes >> typeAttributes("classes")
        artifact2.attributes >> typeAttributes("jar")

        expect:
        def spec = transformer.variantSelector(typeAttributes("classes"))
        spec.transform([artifact1, artifact2]) == artifact1
    }

    def "selects variant with attributes that can be transformed to requested format"() {
        def artifact1 = Stub(ResolvedArtifact)
        def artifact2 = Stub(ResolvedArtifact)

        given:
        artifact1.attributes >> typeAttributes("jar")
        artifact2.attributes >> typeAttributes("dll")

        matchingCache.getTransform(typeAttributes("jar"), typeAttributes("classes")) >> Stub(Transformer)
        matchingCache.getTransform(typeAttributes("dll"), typeAttributes("classes")) >> null

        expect:
        def spec = transformer.variantSelector(typeAttributes("classes"))
        spec.transform([artifact1, artifact2]) == artifact1
    }

    def "selects variant with requested attributes when another variant can be transformed"() {
        def realCache = new ArtifactAttributeMatchingCache(new DefaultArtifactTransformRegistrations(), setupSchema())
        transformer = new DefaultArtifactTransforms(realCache)

        def artifact1 = Stub(ResolvedArtifact)
        def artifact2 = Stub(ResolvedArtifact)

        given:
        artifact1.attributes >> typeAttributes("jar")
        artifact2.attributes >> typeAttributes("classes")

        matchingCache.getTransform(typeAttributes("jar"), typeAttributes("classes")) >> Stub(Transformer)

        expect:
        def spec = transformer.variantSelector(typeAttributes("classes"))
        spec.transform([artifact1, artifact2]) == artifact2
    }

    def "selects no variant when none match"() {
        def realCache = new ArtifactAttributeMatchingCache(new DefaultArtifactTransformRegistrations(), setupSchema())
        transformer = new DefaultArtifactTransforms(realCache)

        def artifact1 = Stub(ResolvedArtifact)
        def artifact2 = Stub(ResolvedArtifact)

        given:
        artifact1.attributes >> typeAttributes("jar")
        artifact2.attributes >> typeAttributes("classes")

        expect:
        def spec = transformer.variantSelector(typeAttributes("dll"))
        spec.transform([artifact1, artifact2]) == null
    }

    private AttributeContainerInternal typeAttributes(String artifactType) {
        def attributeContainer = new DefaultMutableAttributeContainer(immutableAttributesFactory)
        attributeContainer.attribute(ARTIFACT_FORMAT, artifactType)
        attributeContainer.asImmutable()
    }

    interface TestArtifact extends ResolvedArtifact, Buildable {}

    private AttributesSchema setupSchema() {
        def attributesSchema = new DefaultAttributesSchema(new ComponentAttributeMatcher())
        attributesSchema.attribute(ARTIFACT_FORMAT) {
            it.compatibilityRules.assumeCompatibleWhenMissing()
        }
        attributesSchema.attribute(ARTIFACT_CLASSIFIER) {
            it.compatibilityRules.assumeCompatibleWhenMissing()
        }
        attributesSchema.attribute(ARTIFACT_EXTENSION) {
            it.compatibilityRules.assumeCompatibleWhenMissing()
        }
        attributesSchema
    }
}
