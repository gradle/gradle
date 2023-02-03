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

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.component.ArtifactType
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentResolveMetadata
import org.gradle.internal.component.model.ImmutableModuleSources
import org.gradle.internal.component.model.ModuleSource
import org.gradle.internal.resolve.ArtifactResolveException
import org.gradle.internal.resolve.resolver.ArtifactResolver
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import spock.lang.Specification

class ErrorHandlingArtifactResolverTest extends Specification {
    def delegate = Mock(ArtifactResolver)
    def artifactResolver = new ErrorHandlingArtifactResolver(delegate)

    def "wraps resolveArtifact exception as failure"() {
        def moduleVersionId = DefaultModuleVersionIdentifier.newId("org", "name", "1.0")
        def componentArtifactId = Stub(ComponentArtifactIdentifier) {
            getDisplayName() >> "<component-artifact>"
        }
        def componentArtifact = Stub(ComponentArtifactMetadata) {
            getId() >> componentArtifactId
        }
        def moduleSources = ImmutableModuleSources.of(Mock(ModuleSource))
        def artifactResolveResult = Mock(BuildableArtifactResolveResult)
        def failure = new RuntimeException("foo")

        when:
        delegate.resolveArtifact(moduleVersionId, componentArtifact, moduleSources, artifactResolveResult) >> { throw failure }

        and:
        artifactResolver.resolveArtifact(moduleVersionId, componentArtifact, moduleSources, artifactResolveResult)

        then:
        1 * artifactResolveResult.failed(_ as ArtifactResolveException) >> { ArtifactResolveException e ->
            assert e.message == "Could not download <component-artifact>"
            assert e.cause == failure
        }
    }

    def "wraps resolveModuleArtifacts exception as failure"() {
        def componentId = Stub(ComponentIdentifier) {
            getDisplayName() >> "<component>"
        }
        def component = Stub(ComponentResolveMetadata) {
            getId() >> componentId
        }
        def result = Mock(BuildableArtifactSetResolveResult)
        def failure = new RuntimeException("foo")

        when:
        def artifactType = ArtifactType.JAVADOC
        delegate.resolveArtifactsWithType(component, artifactType, result) >> { throw failure }

        and:
        artifactResolver.resolveArtifactsWithType(component, artifactType, result)

        then:
        1 * result.failed(_ as ArtifactResolveException) >> { ArtifactResolveException e ->
            assert e.message == "Could not determine artifacts for <component>"
            assert e.cause == failure
        }
        0 * _._
    }
}
