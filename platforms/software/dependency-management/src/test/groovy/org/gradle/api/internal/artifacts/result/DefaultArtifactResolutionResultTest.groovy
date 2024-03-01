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

import org.gradle.api.artifacts.result.ArtifactResolutionResult
import org.gradle.api.artifacts.result.ComponentArtifactsResult
import org.gradle.api.artifacts.result.UnresolvedComponentResult
import spock.lang.Specification

class DefaultArtifactResolutionResultTest extends Specification {
    ComponentArtifactsResult componentArtifactsResult1 = Mock()
    ComponentArtifactsResult componentArtifactsResult2 = Mock()
    UnresolvedComponentResult unresolvedComponentResult1 = Mock()
    UnresolvedComponentResult unresolvedComponentResult2 = Mock()

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
}
