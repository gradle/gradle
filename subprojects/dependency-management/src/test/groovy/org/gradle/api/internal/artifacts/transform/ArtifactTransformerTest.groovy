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
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.attributes.DefaultArtifactAttributes
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.attributes.DefaultAttributeContainer
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.internal.resolve.ArtifactResolveException
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.ArtifactAttributes.*

class ArtifactTransformerTest extends Specification {
    def resolutionStrategy = Mock(ResolutionStrategyInternal)
    def attributesSchema = new DefaultAttributesSchema()
    def artifactTransforms = Mock(ArtifactTransforms)
    def artifactAttributeMatcher = new ArtifactAttributeMatcher(attributesSchema);
    def transformer = new ArtifactTransformer(artifactTransforms, artifactAttributeMatcher)

    def setup() {
        attributesSchema.attribute(ARTIFACT_FORMAT) {
            it.compatibilityRules.assumeCompatibleWhenMissing()
        }
        attributesSchema.attribute(ARTIFACT_CLASSIFIER) {
            it.compatibilityRules.assumeCompatibleWhenMissing()
        }
        attributesSchema.attribute(ARTIFACT_EXTENSION) {
            it.compatibilityRules.assumeCompatibleWhenMissing()
        }
    }

    def "forwards artifact whose type matches requested format"() {
        def visitor = Mock(ArtifactVisitor)
        def artifact = Stub(ResolvedArtifact)
        def requestAttributes = typeAttributes("classpath")

        given:
        artifact.attributes >> requestAttributes

        when:
        def transformVisitor = transformer.visitor(visitor, requestAttributes)
        transformVisitor.visitArtifact(artifact)

        then:
        1 * visitor.visitArtifact(artifact)
        1 * artifactTransforms.getTransform(requestAttributes, requestAttributes) >> null
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
        def transformVisitor = transformer.visitor(visitor, typeAttributes("classpath"))
        transformVisitor.visitArtifact(artifact)

        then:
        1 * artifactTransforms.getTransform(typeAttributes("zip"), typeAttributes("classpath")) >> transform
        1 * transform.transform(file) >> [transformedFile]
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
        resolutionStrategy.getTransform("lib", "classpath") >> null

        when:
        def transformVisitor = transformer.visitor(visitor, typeAttributes("classpath"))
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
        def transformVisitor = transformer.visitor(visitor, requestAttributes)
        transformVisitor.visitFiles(id, [file])

        then:
        1 * artifactTransforms.getTransform(DefaultArtifactAttributes.forFile(file), requestAttributes) >> null
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
        def transformVisitor = transformer.visitor(visitor, typeAttributes("classpath"))
        transformVisitor.visitFiles(id, [file])

        then:
        1 * artifactTransforms.getTransform(DefaultArtifactAttributes.forFile(file), typeAttributes("classpath")) >> transform
        1 * transform.transform(file) >> [transformedFile]
        1 * visitor.visitFiles(id, [transformedFile])
        0 * _
    }

    def "ignores file whose extension cannot be transformed"() {
        def visitor = Mock(ArtifactVisitor)
        def id = Stub(ComponentIdentifier)
        def file = new File("thing.lib")

        when:
        def transformVisitor = transformer.visitor(visitor, typeAttributes("classpath"))
        transformVisitor.visitFiles(id, [file])

        then:
        1 * artifactTransforms.getTransform(DefaultArtifactAttributes.forFile(file), typeAttributes("classpath")) >> null
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
        artifactTransforms.getTransform(DefaultArtifactAttributes.forFile(file1), typeAttributes("classpath")) >> transform
        artifactTransforms.getTransform(DefaultArtifactAttributes.forFile(file2), typeAttributes("classpath")) >> transform
        transform.transform(file1) >> [transformedFile1]

        def transformVisitor = transformer.visitor(visitor, typeAttributes("classpath"))
        transformVisitor.visitFiles(id, [file1])

        when:
        transformVisitor.visitFiles(id, [file1])

        then:
        1 * visitor.visitFiles(id, [transformedFile1])
        0 * _

        when:
        transformer.visitor(visitor, typeAttributes("classpath")).visitFiles(id, [file1])

        then:
        1 * visitor.visitFiles(id, [transformedFile1])
        0 * _

        when:
        transformer.visitor(visitor, typeAttributes("classpath")).visitFiles(id, [file1, file2])

        then:
        1 * artifactTransforms.getTransform(DefaultArtifactAttributes.forFile(file1), typeAttributes("classpath")) >> transform
        1 * transform.transform(file2) >> [transformedFile2]
        1 * visitor.visitFiles(id, [transformedFile1, transformedFile2])
        0 * _

        when:
        transformer.visitor(visitor, typeAttributes("classpath")).visitFiles(id, [file1, file2])

        then:
        1 * visitor.visitFiles(id, [transformedFile1, transformedFile2])
        0 * _
    }

    def "selects variant with requested attributes"() {
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

        artifactTransforms.getTransform(typeAttributes("jar"), typeAttributes("classes")) >> Stub(Transformer)
        artifactTransforms.getTransform(typeAttributes("dll"), typeAttributes("classes")) >> null

        expect:
        def spec = transformer.variantSelector(typeAttributes("classes"))
        spec.transform([artifact1, artifact2]) == artifact1
    }

    def "selects variant with requested attributes when another variant can be transformed"() {
        def artifact1 = Stub(ResolvedArtifact)
        def artifact2 = Stub(ResolvedArtifact)

        given:
        artifact1.attributes >> typeAttributes("jar")
        artifact2.attributes >> typeAttributes("classes")

        artifactTransforms.getTransform(typeAttributes("jar"), typeAttributes("classes")) >> Stub(Transformer)

        expect:
        def spec = transformer.variantSelector(typeAttributes("classes"))
        spec.transform([artifact1, artifact2]) == artifact2
    }

    def "selects no variant when none match"() {
        def artifact1 = Stub(ResolvedArtifact)
        def artifact2 = Stub(ResolvedArtifact)

        given:
        artifact1.attributes >> typeAttributes("jar")
        artifact2.attributes >> typeAttributes("classes")

        expect:
        def spec = transformer.variantSelector(typeAttributes("dll"))
        spec.transform([artifact1, artifact2]) == null
    }

    private static AttributeContainer typeAttributes(String artifactType) {
        def attributeContainer = new DefaultAttributeContainer()
        attributeContainer.attribute(ARTIFACT_FORMAT, artifactType.toString())
        attributeContainer.asImmutable()
    }

    interface TestArtifact extends ResolvedArtifact, Buildable {}
}
