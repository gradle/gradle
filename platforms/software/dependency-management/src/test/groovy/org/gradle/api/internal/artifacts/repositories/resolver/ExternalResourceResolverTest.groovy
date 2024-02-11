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

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.repositories.descriptor.UrlRepositoryDescriptor
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultArtifactMetadataSource
import org.gradle.api.internal.artifacts.repositories.metadata.ImmutableMetadataSources
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataArtifactProvider
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataSource
import org.gradle.api.internal.artifacts.repositories.metadata.MutableModuleMetadataFactory
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.ImmutableModuleSources
import org.gradle.internal.component.model.ModuleSource
import org.gradle.internal.resolve.ArtifactResolveException
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor
import spock.lang.Specification

class ExternalResourceResolverTest extends Specification {
    String name = "TestResolver"
    ExternalResourceRepository repository = Mock()
    BuildableArtifactFileResolveResult artifactResult = Mock()
    BuildableModuleComponentMetaDataResolveResult metadataResult = Mock()
    ModuleComponentArtifactIdentifier artifactIdentifier = Stub() {
        getDisplayName() >> '<some-artifact>'
    }
    ModuleComponentArtifactMetadata artifact = Stub() {
        getId() >> artifactIdentifier
    }
    ModuleSource moduleSource = Mock()
    File downloadedFile = Mock(File)
    CacheAwareExternalResourceAccessor resourceAccessor = Stub()
    LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder = Mock()
    FileStore<ModuleComponentArtifactIdentifier> fileStore = Stub()
    ExternalResourceArtifactResolver artifactResolver = Mock()
    ImmutableMetadataSources metadataSources = Mock()
    ExternalResourceResolver resolver

    def setup() {
        resolver = new TestResolver(Stub(UrlRepositoryDescriptor), true, repository, resourceAccessor, locallyAvailableResourceFinder, fileStore, metadataSources, Stub(MetadataArtifactProvider))
        resolver.artifactResolver = artifactResolver
    }

    def reportsNotFoundArtifactResolveResult() {
        when:
        resolver.remoteAccess.resolveArtifact(artifact, ImmutableModuleSources.of(moduleSource), artifactResult)

        then:
        1 * artifactResolver.resolveArtifact(artifact, _) >> null
        1 * artifactResult.notFound(artifactIdentifier)
        0 * artifactResult._
        0 * artifactResolver._
    }

    def reportsFailedArtifactResolveResult() {
        when:
        resolver.remoteAccess.resolveArtifact(artifact, ImmutableModuleSources.of(moduleSource), artifactResult)

        then:
        1 * artifactResolver.resolveArtifact(artifact, _) >> {
            throw new RuntimeException("DOWNLOAD FAILURE")
        }
        1 * artifactResult.failed(_) >> { ArtifactResolveException exception ->
            assert exception.message == "Could not download <some-artifact>"
            assert exception.cause.message == "DOWNLOAD FAILURE"
        }
        0 * artifactResult._
        0 * artifactResolver._
    }

    def reportsResolvedArtifactResolveResult() {
        when:
        resolver.remoteAccess.resolveArtifact(artifact, ImmutableModuleSources.of(moduleSource), artifactResult)

        then:
        1 * artifactResolver.resolveArtifact(artifact, _) >> Stub(LocallyAvailableExternalResource) {
            getFile() >> downloadedFile
        }
        1 * artifactResult.resolved(downloadedFile)
        0 * artifactResult._
        0 * artifactResolver._
    }

    def reportsResolvedArtifactResolveResultWithSnapshotVersion() {
        given:
        artifactIsTimestampedSnapshotVersion()

        when:
        resolver.remoteAccess.resolveArtifact(artifact, ImmutableModuleSources.of(moduleSource), artifactResult)

        then:
        1 * artifactResolver.resolveArtifact(artifact, _) >> Stub(LocallyAvailableExternalResource) {
            getFile() >> downloadedFile
        }
        1 * artifactResult.resolved(downloadedFile)
        0 * artifactResult._
        0 * artifactResolver._
    }

    def artifactIsTimestampedSnapshotVersion() {
        _ * moduleSource.timestamp >> "1.0-20100101.120001-1"
    }

    def "doesn't try to fetch artifact when module metadata file is missing"() {
        given:
        def id = Stub(ModuleComponentIdentifier)

        when:
        resolver.remoteAccess.resolveComponentMetaData(id, Stub(ComponentOverrideMetadata), metadataResult)

        then:
        1 * metadataSources.sources() >> ImmutableList.of(Stub(MetadataSource) { create(_, _, _, _, _, _) >> null })
        1 * metadataResult.missing()
        0 * _
    }

    def "tries to fetch artifact when module metadata file is missing and legacy mode is active"() {
        given:
        def id = Stub(ModuleComponentIdentifier)
        def metadata = Stub(ComponentOverrideMetadata)
        metadata.artifact >> null

        when:
        resolver.remoteAccess.resolveComponentMetaData(id, metadata, metadataResult)

        then:
        1 * metadataSources.sources() >> ImmutableList.of(new DefaultArtifactMetadataSource(Mock(MutableModuleMetadataFactory)))
        1 * artifactResolver.artifactExists({ it.componentId.is(id) && it.name.type == 'jar' }, _)
        1 * metadataResult.missing()
        0 * _
    }

}
