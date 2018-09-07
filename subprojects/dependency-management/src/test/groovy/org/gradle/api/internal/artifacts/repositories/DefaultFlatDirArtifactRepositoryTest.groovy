/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.InstantiatorFactory
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResolver
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.collections.ImmutableFileCollection
import org.gradle.api.internal.filestore.ivy.ArtifactIdentifierFileStore
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultFlatDirArtifactRepositoryTest extends Specification {
    final FileResolver fileResolver = Mock()
    final ExternalResourceRepository resourceRepository = Mock()
    final RepositoryTransport repositoryTransport = Mock()
    final RepositoryTransportFactory transportFactory = Mock()
    final LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder = Mock()
    final ArtifactIdentifierFileStore artifactIdentifierFileStore = Stub()
    final ImmutableModuleIdentifierFactory moduleIdentifierFactory = Mock()
    final IvyMutableModuleMetadataFactory metadataFactory = new IvyMutableModuleMetadataFactory(moduleIdentifierFactory, TestUtil.attributesFactory())

    final DefaultFlatDirArtifactRepository repository = new DefaultFlatDirArtifactRepository(fileResolver, transportFactory, locallyAvailableResourceFinder, artifactIdentifierFileStore, moduleIdentifierFactory, metadataFactory, Mock(InstantiatorFactory), Mock(ObjectFactory))

    def "creates a repository with multiple root directories"() {
        given:
        def dir1 = new File('a')
        def dir2 = new File('b')
        _ * fileResolver.resolveFiles(['a', 'b']) >> ImmutableFileCollection.of(dir1, dir2)
        _ * repositoryTransport.repository >> resourceRepository

        and:
        repository.name = 'repo-name'
        repository.dirs('a', 'b')

        when:
        def repo = repository.createResolver()

        then:
        1 * transportFactory.createTransport("file", "repo-name", []) >> repositoryTransport

        and:
        repo instanceof IvyResolver
        def expectedPatterns = [
                "${dir1.toURI()}/[artifact]-[revision](-[classifier]).[ext]",
                "${dir1.toURI()}/[artifact](-[classifier]).[ext]",
                "${dir2.toURI()}/[artifact]-[revision](-[classifier]).[ext]",
                "${dir2.toURI()}/[artifact](-[classifier]).[ext]"
        ]
        repo.ivyPatterns == []
        repo.artifactPatterns == expectedPatterns
    }

    def "fails when no directories specified"() {
        given:
        _ * fileResolver.resolveFiles(_) >> ImmutableFileCollection.of()

        when:
        repository.createResolver()

        then:
        def e = thrown InvalidUserDataException
        e.message == 'You must specify at least one directory for a flat directory repository.'
    }
}
