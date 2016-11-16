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

package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.api.Transformer
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import spock.lang.Specification

class ArtifactTransformerTest extends Specification {
    def resolutionStrategy = Mock(ResolutionStrategyInternal)
    def transformer = new ArtifactTransformer("classpath", resolutionStrategy)

    def "forwards artifact whose type matches requested format"() {
        def visitor = Mock(ArtifactVisitor)
        def artifact = Stub(ResolvedArtifact)

        given:
        artifact.type >> "classpath"

        when:
        def transformVisitor = transformer.visitor(visitor)
        transformVisitor.visitArtifact(artifact)

        then:
        1 * visitor.visitArtifact(artifact)
        0 * _
    }

    def "applies transform lazily to artifact whose type does not matches requested format"() {
        def visitor = Mock(ArtifactVisitor)
        def artifact = Stub(ResolvedArtifact)
        def transform = Mock(Transformer)
        def file = new File("thing.zip")
        def transformedFile = new File("thing.classpath")
        def transformedArtifact = null

        given:
        artifact.type >> "zip"

        when:
        def transformVisitor = transformer.visitor(visitor)
        transformVisitor.visitArtifact(artifact)

        then:
        1 * resolutionStrategy.getTransform("zip", "classpath") >> transform
        1 * visitor.visitArtifact(_) >> { ResolvedArtifact a -> transformedArtifact = a }
        0 * _

        and:
        transformedArtifact.type == "classpath"

        when:
        def result = transformedArtifact.file

        then:
        1 * transform.transform(file) >> transformedFile
        0 * _

        and:
        result == transformedFile

        when:
        transformedArtifact.file

        then:
        0 * _
    }

    def "ignores artifact whose type cannot be transformed"() {
        def visitor = Mock(ArtifactVisitor)
        def artifact = Stub(ResolvedArtifact)

        given:
        artifact.type >> "lib"
        resolutionStrategy.getTransform("lib", "classpath") >> null

        when:
        def transformVisitor = transformer.visitor(visitor)
        transformVisitor.visitArtifact(artifact)

        then:
        0 * visitor._
    }

    def "forwards file whose extension matches requested format"() {
        def visitor = Mock(ArtifactVisitor)
        def id = Stub(ComponentIdentifier)
        def file = new File("thing.classpath")

        when:
        def transformVisitor = transformer.visitor(visitor)
        transformVisitor.visitFiles(id, [file])

        then:
        1 * visitor.visitFiles(id , [file])
        0 * _
    }

    def "applies transform to file whose extension does not match requested format"() {
        def visitor = Mock(ArtifactVisitor)
        def id = Stub(ComponentIdentifier)
        def transform = Mock(Transformer)
        def file = new File("thing.zip")
        def transformedFile = new File("thing.classpath")

        when:
        def transformVisitor = transformer.visitor(visitor)
        transformVisitor.visitFiles(id, [file])

        then:
        1 * resolutionStrategy.getTransform("zip", "classpath") >> transform
        1 * transform.transform(file) >> transformedFile
        1 * visitor.visitFiles(id, [transformedFile])
        0 * _
    }

    def "ignores file whose extension cannot be transformed"() {
        def visitor = Mock(ArtifactVisitor)
        def id = Stub(ComponentIdentifier)
        def file = new File("thing.lib")

        when:
        def transformVisitor = transformer.visitor(visitor)
        transformVisitor.visitFiles(id, [file])

        then:
        1 * resolutionStrategy.getTransform("lib", "classpath") >> null
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
        resolutionStrategy.getTransform("zip", "classpath") >> transform
        transform.transform(file1) >> transformedFile1

        def transformVisitor = transformer.visitor(visitor)
        transformVisitor.visitFiles(id, [file1])

        when:
        transformVisitor.visitFiles(id, [file1])

        then:
        1 * visitor.visitFiles(id, [transformedFile1])
        0 * _

        when:
        transformer.visitor(visitor).visitFiles(id, [file1])

        then:
        1 * visitor.visitFiles(id, [transformedFile1])
        0 * _

        when:
        transformer.visitor(visitor).visitFiles(id, [file1, file2])

        then:
        1 * resolutionStrategy.getTransform("zip", "classpath") >> transform
        1 * transform.transform(file2) >> transformedFile2
        1 * visitor.visitFiles(id, [transformedFile1, transformedFile2])
        0 * _

        when:
        transformer.visitor(visitor).visitFiles(id, [file1, file2])

        then:
        1 * visitor.visitFiles(id, [transformedFile1, transformedFile2])
        0 * _
    }
}
