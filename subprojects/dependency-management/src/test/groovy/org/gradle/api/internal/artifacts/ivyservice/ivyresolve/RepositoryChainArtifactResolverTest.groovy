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

import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentOptionalArtifactMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.ImmutableModuleSources
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.resolve.result.DefaultBuildableResolvableArtifactResult
import org.gradle.internal.resources.ProjectLeaseRegistry
import org.gradle.util.TestUtil
import spock.lang.Specification

class RepositoryChainArtifactResolverTest extends Specification {
    final ownerId = DefaultModuleVersionIdentifier.newId("com.example", "foo", "1.0")
    final calculatedValueContainerFactory = new CalculatedValueContainerFactory(Stub(ProjectLeaseRegistry), TestUtil.services())

    def repo1 = Mock(ModuleComponentRepository) {
        getId() >> "repo1"
    }
    def localAccess2 = Mock(ModuleComponentRepositoryAccess)
    def remoteAccess2 = Mock(ModuleComponentRepositoryAccess)
    def repo2 = Mock(ModuleComponentRepository) {
        getLocalAccess() >> localAccess2
        getRemoteAccess() >> remoteAccess2
        getId() >> "repo2"
    }
    def repo2Source = new RepositoryChainModuleSource(repo2)

    def resolver = new RepositoryChainArtifactResolver(calculatedValueContainerFactory)

    def setup() {
        resolver.add(repo1)
        resolver.add(repo2)
    }

    def "locates artifact with local access in repository defined by module source"() {
        final artifact = new DefaultModuleComponentArtifactMetadata(new DefaultModuleComponentIdentifier(ownerId.getModule(), "1.0"), new DefaultIvyArtifactName(ownerId.name, "jar", "jar"))
        def artifactFile = Mock(File)
        def moduleSources = ImmutableModuleSources.of(repo2Source)
        def resolvableArtifactResult = new DefaultBuildableResolvableArtifactResult()
        when:
        resolver.resolveArtifact(ownerId, artifact, moduleSources, resolvableArtifactResult)

        then:
        0 * _._

        when:
        resolvableArtifactResult.result.file // triggers access
        then:
        1 * repo2.getLocalAccess() >> localAccess2
        1 * localAccess2.resolveArtifact(artifact, moduleSources, _) >> {
            it[2].resolved(artifactFile)
        }
        0 * _._
        and:
        resolvableArtifactResult.result.file == artifactFile
    }

    def "locates artifact with remote access in repository defined by module source"() {
        final artifact = new DefaultModuleComponentArtifactMetadata(new DefaultModuleComponentIdentifier(ownerId.getModule(), "1.0"), new DefaultIvyArtifactName(ownerId.name, "jar", "jar"))
        def artifactFile = Mock(File)
        def moduleSources = ImmutableModuleSources.of(repo2Source)
        def resolvableArtifactResult = new DefaultBuildableResolvableArtifactResult()
        when:
        resolver.resolveArtifact(ownerId, artifact, moduleSources, resolvableArtifactResult)

        then:
        0 * _._

        when:
        resolvableArtifactResult.result.file // triggers access
        then:
        1 * repo2.getLocalAccess() >> localAccess2
        1 * localAccess2.resolveArtifact(artifact, moduleSources, _)
        1 * repo2.getRemoteAccess() >> remoteAccess2
        1 * remoteAccess2.resolveArtifact(artifact, moduleSources, _) >> {
            it[2].resolved(artifactFile)
        }
        0 * _._

        and:
        resolvableArtifactResult.result.file == artifactFile
    }

    def "locates an optional artifact from local access"() {
        final artifact = new ModuleComponentOptionalArtifactMetadata(new DefaultModuleComponentIdentifier(ownerId.getModule(), "1.0"), new DefaultIvyArtifactName(ownerId.name, "jar", "jar"))
        def artifactFile = Mock(File)
        def moduleSources = ImmutableModuleSources.of(repo2Source)
        def resolvableArtifactResult = new DefaultBuildableResolvableArtifactResult()
        when:
        resolver.resolveArtifact(ownerId, artifact, moduleSources, resolvableArtifactResult)

        then:
        1 * repo2.getLocalAccess() >> localAccess2
        1 * localAccess2.resolveArtifact(artifact, moduleSources, _) >> {
            it[2].resolved(artifactFile)
        }
        0 * _._

        when:
        resolvableArtifactResult.result.file // searches again
        then:
        1 * repo2.getLocalAccess() >> localAccess2
        1 * localAccess2.resolveArtifact(artifact, moduleSources, _) >> {
            it[2].resolved(artifactFile)
        }
        0 * _._

        and:
        resolvableArtifactResult.result.file == artifactFile
    }

    def "locates an optional artifact from remote access"() {
        final artifact = new ModuleComponentOptionalArtifactMetadata(new DefaultModuleComponentIdentifier(ownerId.getModule(), "1.0"), new DefaultIvyArtifactName(ownerId.name, "jar", "jar"))
        def artifactFile = Mock(File)
        def moduleSources = ImmutableModuleSources.of(repo2Source)
        def resolvableArtifactResult = new DefaultBuildableResolvableArtifactResult()
        when:
        resolver.resolveArtifact(ownerId, artifact, moduleSources, resolvableArtifactResult)

        then:
        1 * repo2.getLocalAccess() >> localAccess2
        1 * localAccess2.resolveArtifact(artifact, moduleSources, _)
        1 * repo2.getRemoteAccess() >> remoteAccess2
        1 * remoteAccess2.resolveArtifact(artifact, moduleSources, _) >> {
            it[2].resolved(artifactFile)
        }
        0 * _._

        when:
        resolvableArtifactResult.result.file // searches again
        then:
        1 * repo2.getLocalAccess() >> localAccess2
        1 * localAccess2.resolveArtifact(artifact, moduleSources, _)
        1 * repo2.getRemoteAccess() >> remoteAccess2
        1 * remoteAccess2.resolveArtifact(artifact, moduleSources, _) >> {
            it[2].resolved(artifactFile)
        }
        0 * _._

        and:
        resolvableArtifactResult.result.file == artifactFile
    }

    def "does not find an optional artifact that does not exist"() {
        final artifact = new ModuleComponentOptionalArtifactMetadata(new DefaultModuleComponentIdentifier(ownerId.getModule(), "1.0"), new DefaultIvyArtifactName(ownerId.name, "jar", "jar"))
        def moduleSources = ImmutableModuleSources.of(repo2Source)
        def resolvableArtifactResult = new DefaultBuildableResolvableArtifactResult()
        when:
        resolver.resolveArtifact(ownerId, artifact, moduleSources, resolvableArtifactResult)

        then:
        1 * repo2.getLocalAccess() >> localAccess2
        1 * localAccess2.resolveArtifact(artifact, moduleSources, _)
        1 * repo2.getRemoteAccess() >> remoteAccess2
        1 * remoteAccess2.resolveArtifact(artifact, moduleSources, _)
        0 * _._
        and:
        !resolvableArtifactResult.exists()
    }
}
