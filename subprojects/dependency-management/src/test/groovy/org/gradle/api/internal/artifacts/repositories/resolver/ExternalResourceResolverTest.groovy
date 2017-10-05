/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories.resolver

import org.gradle.api.artifacts.ArtifactIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.model.ModuleSource
import org.gradle.internal.resolve.ArtifactResolveException
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor
import spock.lang.Specification

class ExternalResourceResolverTest extends Specification {
    String name = "TestResolver"
    ExternalResourceRepository repository = Mock()
    VersionLister versionLister = Mock()
    LocallyAvailableResourceFinder<ArtifactIdentifier> locallyAvailableResourceFinder = Mock()
    BuildableArtifactResolveResult result = Mock()
    ModuleComponentArtifactIdentifier artifactIdentifier = Stub() {
        getDisplayName() >> '<some-artifact>'
    }
    ModuleComponentArtifactMetadata artifact = Stub() {
        getId() >> artifactIdentifier
    }
    ModuleSource moduleSource = Mock()
    File downloadedFile = Mock(File)
    CacheAwareExternalResourceAccessor resourceAccessor = Stub()
    FileStore<ModuleComponentArtifactMetadata> fileStore = Stub()
    ImmutableModuleIdentifierFactory moduleIdentifierFactory = Stub()
    ExternalResourceArtifactResolver artifactResolver = Mock()
    ExternalResourceResolver resolver

    def setup() {
        resolver = new TestResolver(name, true, repository, resourceAccessor, versionLister, locallyAvailableResourceFinder, fileStore, moduleIdentifierFactory, Mock(FileResourceRepository))
        resolver.artifactResolver = artifactResolver
    }

    def reportsNotFoundArtifactResolveResult() {
        when:
        resolver.remoteAccess.resolveArtifact(artifact, moduleSource, result)

        then:
        1 * artifactResolver.resolveArtifact(artifact, _) >> null
        1 * result.notFound(artifactIdentifier)
        0 * result._
        0 * artifactResolver._
    }

    def reportsFailedArtifactResolveResult() {
        when:
        resolver.remoteAccess.resolveArtifact(artifact, moduleSource, result)

        then:
        1 * artifactResolver.resolveArtifact(artifact, _) >> {
            throw new RuntimeException("DOWNLOAD FAILURE")
        }
        1 * result.failed(_) >> { ArtifactResolveException exception ->
            assert exception.message == "Could not download <some-artifact>"
            assert exception.cause.message == "DOWNLOAD FAILURE"
        }
        0 * result._
        0 * artifactResolver._
    }

    def reportsResolvedArtifactResolveResult() {
        when:
        resolver.remoteAccess.resolveArtifact(artifact, moduleSource, result)

        then:
        1 * artifactResolver.resolveArtifact(artifact, _) >> Stub(LocallyAvailableExternalResource) {
            getFile() >> downloadedFile
        }
        1 * result.resolved(downloadedFile)
        0 * result._
        0 * artifactResolver._
    }

    def reportsResolvedArtifactResolveResultWithSnapshotVersion() {
        given:
        artifactIsTimestampedSnapshotVersion()

        when:
        resolver.remoteAccess.resolveArtifact(artifact, moduleSource, result)

        then:
        1 * artifactResolver.resolveArtifact(artifact, _) >> Stub(LocallyAvailableExternalResource) {
            getFile() >> downloadedFile
        }
        1 * result.resolved(downloadedFile)
        0 * result._
        0 * artifactResolver._
    }

    def artifactIsTimestampedSnapshotVersion() {
        _ * moduleSource.timestamp >> "1.0-20100101.120001-1"
    }


}
