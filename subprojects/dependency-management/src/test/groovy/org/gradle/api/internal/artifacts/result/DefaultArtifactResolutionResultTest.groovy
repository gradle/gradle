/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.result

import org.gradle.api.artifacts.result.*
import org.gradle.api.component.Artifact
import spock.lang.Specification

class DefaultArtifactResolutionResultTest extends Specification {
    ComponentArtifactsResult componentArtifactsResult1 = Mock()
    ComponentArtifactsResult componentArtifactsResult2 = Mock()
    UnresolvedComponentResult unresolvedComponentResult1 = Mock()
    UnresolvedComponentResult unresolvedComponentResult2 = Mock()
    ResolvedArtifactResult resolvedArtifactResult1 = Mock()
    ResolvedArtifactResult resolvedArtifactResult2 = Mock()
    UnresolvedArtifactResult unresolvedArtifactResult = Mock()

    def "component results match the ones passed into the constructor"() {
        ArtifactResolutionResult artifactResolutionResult = new DefaultArtifactResolutionResult([componentArtifactsResult1, unresolvedComponentResult1] as LinkedHashSet)

        expect:
        artifactResolutionResult.components.size() == 2
        artifactResolutionResult.components.contains(componentArtifactsResult1)
        artifactResolutionResult.components.contains(unresolvedComponentResult1)
    }

    def "gets resolved components for component artifact results"() {
        ArtifactResolutionResult artifactResolutionResult = new DefaultArtifactResolutionResult([componentArtifactsResult1, componentArtifactsResult2] as LinkedHashSet)

        when:
        Set<ComponentArtifactsResult> resolvedComponents = artifactResolutionResult.resolvedComponents

        then:
        resolvedComponents.size() == 2
        resolvedComponents.each { assert it instanceof ComponentArtifactsResult }
    }

    def "gets resolved components for unresolved artifact results"() {
        ArtifactResolutionResult artifactResolutionResult = new DefaultArtifactResolutionResult([unresolvedComponentResult1, unresolvedComponentResult2] as LinkedHashSet)

        when:
        Set<ComponentArtifactsResult> resolvedComponents = artifactResolutionResult.resolvedComponents

        then:
        resolvedComponents.size() == 0
    }

    def "gets artifacts for resolved component results"() {
        given:
        Set<ArtifactResult> artifacts1 = new HashSet<ArtifactResult>()
        artifacts1 << resolvedArtifactResult1
        Set<ArtifactResult> artifacts2 = new HashSet<ArtifactResult>()
        artifacts2 << resolvedArtifactResult2
        File artifact1 = new File('artifact1.jar')
        File artifact2 = new File('artifact2.jar')
        ArtifactResolutionResult artifactResolutionResult = new DefaultArtifactResolutionResult([componentArtifactsResult1, componentArtifactsResult2] as LinkedHashSet)

        when:
        Set<File> artifactFiles = artifactResolutionResult.artifactFiles

        then:
        1 * componentArtifactsResult1.getArtifacts(Artifact) >> artifacts1
        1 * componentArtifactsResult2.getArtifacts(Artifact) >> artifacts2
        1 * resolvedArtifactResult1.getFile() >> artifact1
        1 * resolvedArtifactResult2.getFile() >> artifact2
        artifactFiles.size() == 2
        artifactFiles == [artifact1, artifact2] as Set
    }

    def "throws exception for unresolved artifacts"() {
        given:
        Set<ArtifactResult> artifacts1 = new HashSet<ArtifactResult>()
        artifacts1 << resolvedArtifactResult1
        Set<ArtifactResult> artifacts2 = new HashSet<ArtifactResult>()
        artifacts2 << unresolvedArtifactResult
        File artifact1 = new File('artifact1.jar')
        ArtifactResolutionResult artifactResolutionResult = new DefaultArtifactResolutionResult([componentArtifactsResult1, componentArtifactsResult2] as LinkedHashSet)

        when:
        artifactResolutionResult.artifactFiles

        then:
        1 * componentArtifactsResult1.getArtifacts(Artifact) >> artifacts1
        1 * componentArtifactsResult2.getArtifacts(Artifact) >> artifacts2
        1 * resolvedArtifactResult1.getFile() >> artifact1
        0 * resolvedArtifactResult2.getFile()
        1 * unresolvedArtifactResult.failure >> new RuntimeException('something went wrong')
        Throwable t = thrown(UnresolvedArtifactFileException)
        t.message == 'Failed to resolve artifact file'
    }
}
