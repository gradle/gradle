/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolver
import org.gradle.api.internal.artifacts.ivyservice.ArtifactType
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactSetResolveResult
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactIdentifier
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactMetaData
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData
import spock.lang.Specification

class ErrorHandlingArtifactResolverTest extends Specification {
    def delegate = Mock(ArtifactResolver)
    def artifactResolver = new ErrorHandlingArtifactResolver(delegate)
    def artifactSetResolveResult = Mock(BuildableArtifactSetResolveResult)

    def "wraps resolveArtifact exception as failure"() {
        def componentArtifactId = Stub(ComponentArtifactIdentifier) {
            getDisplayName() >> "component-artifact"
        }
        def componentArtifact = Stub(ComponentArtifactMetaData) {
            getId() >> componentArtifactId
        }
        def moduleSource = Mock(ModuleSource)
        def artifactResolveResult = Mock(BuildableArtifactResolveResult)
        def failure = new RuntimeException("foo")

        when:
        delegate.resolveArtifact(componentArtifact, moduleSource, artifactResolveResult) >> { throw failure }

        and:
        artifactResolver.resolveArtifact(componentArtifact, moduleSource, artifactResolveResult)

        then:
        1 * artifactResolveResult.failed(_ as ArtifactResolveException) >> { ArtifactResolveException e ->
            assert e.message == "Could not download artifact 'component-artifact'"
            assert e.cause == failure
        }
    }

    def "wraps resolveModuleArtifacts exception as failure"() {
        def componentId = Stub(ComponentIdentifier) {
            getDisplayName() >> "component"
        }
        def component = Stub(ComponentMetaData) {
            getComponentId() >> componentId
        }
        def context = Stub(ArtifactType) {
            getId() >> "artifact-context"
        }
        def result = Mock(BuildableArtifactSetResolveResult)
        def failure = new RuntimeException("foo")

        when:
        delegate.resolveModuleArtifacts(component, context, result) >> { throw failure }

        and:
        artifactResolver.resolveModuleArtifacts(component, context, result)

        then:
        1 * result.failed(_ as ArtifactResolveException) >> { ArtifactResolveException e ->
            assert e.message == "Could not determine artifacts for component 'component'"
            assert e.cause == failure
        }
    }
}
