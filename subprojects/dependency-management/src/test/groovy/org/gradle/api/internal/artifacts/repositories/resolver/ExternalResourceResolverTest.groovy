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
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor
import org.gradle.internal.resource.transport.ExternalResourceRepository
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
    ExternalResourceResolver resolver

    def setup() {
        //We use a spy here to avoid dealing with all the overhead ivys basicresolver brings in here.
        resolver = Spy(ExternalResourceResolver, constructorArgs: [name, true, repository, resourceAccessor, versionLister, locallyAvailableResourceFinder, fileStore, moduleIdentifierFactory])
    }

    def reportsNotFoundArtifactResolveResult() {
        given:
        artifactIsMissing()

        when:
        resolver.resolveArtifact(artifact, moduleSource, result)

        then:
        1 * result.notFound(artifactIdentifier)
        0 * result._
    }

    def reportsFailedArtifactResolveResult() {
        given:
        downloadIsFailing(new IOException("DOWNLOAD FAILURE"))

        when:
        resolver.resolveArtifact(artifact, moduleSource, result)

        then:
        1 * result.failed(_) >> { ArtifactResolveException exception ->
            assert exception.message == "Could not download <some-artifact>"
            assert exception.cause.message == "DOWNLOAD FAILURE"
        }
        0 * result._
    }

    def reportsResolvedArtifactResolveResult() {
        given:
        artifactCanBeResolved()

        when:
        resolver.resolveArtifact(artifact, moduleSource, result)

        then:
        1 * result.resolved(_)
        0 * result._
    }

    def reportsResolvedArtifactResolveResultWithSnapshotVersion() {
        given:
        artifactIsTimestampedSnapshotVersion()
        artifactCanBeResolved()

        when:
        resolver.resolveArtifact(artifact, moduleSource, result)

        then:
        1 * result.resolved(_)
        0 * result._
    }

    def artifactIsTimestampedSnapshotVersion() {
        _ * moduleSource.timestamp >> "1.0-20100101.120001-1"
    }

    def artifactIsMissing() {
        resolver.download(_, _, _) >> null
    }

    def downloadIsFailing(IOException failure) {
        resolver.download(_, _, _) >> {
            throw failure
        }
    }

    def artifactCanBeResolved() {
        resolver.download(_, _, _) >> downloadedFile
    }
}
