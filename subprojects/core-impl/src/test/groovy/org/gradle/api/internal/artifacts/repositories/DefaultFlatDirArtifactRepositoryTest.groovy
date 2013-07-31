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

import org.apache.ivy.core.module.id.ArtifactRevisionId
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ExternalResourceResolverAdapter
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResolver
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder
import org.gradle.api.internal.externalresource.transport.ExternalResourceRepository
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.collections.SimpleFileCollection
import spock.lang.Specification

class DefaultFlatDirArtifactRepositoryTest extends Specification {
    final FileResolver fileResolver = Mock()
    final ExternalResourceRepository resourceRepository = Mock()
    final RepositoryTransport repositoryTransport = Mock()
    final RepositoryTransportFactory transportFactory = Mock()
    final LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder = Mock()
    final MetaDataParser metaDataParser = Mock()
    final ModuleMetadataProcessor metadataProcessor = Mock()
    final DefaultFlatDirArtifactRepository repository = new DefaultFlatDirArtifactRepository(
            fileResolver, transportFactory, locallyAvailableResourceFinder, metaDataParser, metadataProcessor)

    def "creates a repository with multiple root directories"() {
        given:
        def dir1 = new File('a')
        def dir2 = new File('b')
        _ * fileResolver.resolveFiles(['a', 'b']) >> new SimpleFileCollection(dir1, dir2)
        _ * repositoryTransport.repository >> resourceRepository

        and:
        repository.name = 'repo-name'
        repository.dirs('a', 'b')

        when:
        def resolver = repository.createResolver()

        then:
        1 * transportFactory.createFileTransport("repo-name") >> repositoryTransport

        and:
        resolver instanceof ExternalResourceResolverAdapter
        def repo = resolver.resolver
        repo instanceof IvyResolver
        def expectedPatterns = [
                "$dir1.absolutePath/[artifact]-[revision](-[classifier]).[ext]",
                "$dir1.absolutePath/[artifact](-[classifier]).[ext]",
                "$dir2.absolutePath/[artifact]-[revision](-[classifier]).[ext]",
                "$dir2.absolutePath/[artifact](-[classifier]).[ext]"
        ]
        repo.ivyPatterns == []
        repo.artifactPatterns == expectedPatterns
        repo.allownomd
    }

    def "fails when no directories specified"() {
        given:
        _ * fileResolver.resolveFiles(_) >> new SimpleFileCollection()

        when:
        repository.createResolver()

        then:
        InvalidUserDataException e = thrown()
        e.message == 'You must specify at least one directory for a flat directory repository.'
    }
}
