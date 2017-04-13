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
import org.gradle.api.artifacts.repositories.AuthenticationContainer
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.api.internal.artifacts.repositories.resolver.MavenResolver
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.filestore.ivy.ArtifactIdentifierFileStore
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.resource.cached.ExternalResourceFileStore
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.internal.resource.transport.ExternalResourceRepository
import spock.lang.Specification

class DefaultMavenArtifactRepositoryTest extends Specification {
    final FileResolver resolver = Mock()
    final RepositoryTransportFactory transportFactory = Mock()
    final LocallyAvailableResourceFinder locallyAvailableResourceFinder = Mock()
    final ExternalResourceRepository resourceRepository = Mock()
    final ArtifactIdentifierFileStore artifactIdentifierFileStore = Stub()
    final ExternalResourceFileStore externalResourceFileStore = Stub()
    final MetaDataParser pomParser = Stub()
    final AuthenticationContainer authenticationContainer = Stub()
    final ImmutableModuleIdentifierFactory moduleIdentifierFactory = Stub()

    final DefaultMavenArtifactRepository repository = new DefaultMavenArtifactRepository(
        resolver, transportFactory, locallyAvailableResourceFinder, DirectInstantiator.INSTANCE, artifactIdentifierFileStore, pomParser, authenticationContainer, moduleIdentifierFactory, externalResourceFileStore)

    def "creates local repository"() {
        given:
        def file = new File('repo')
        def uri = file.toURI()
        _ * resolver.resolveUri('repo-dir') >> uri
        transportFactory.createTransport('file', 'repo', _) >> transport()

        and:
        repository.name = 'repo'
        repository.url = 'repo-dir'

        when:
        def repo = repository.createRealResolver()

        then:
        repo instanceof MavenResolver
        repo.root == uri
    }

    def "creates http repository"() {
        given:
        def uri = new URI("http://localhost:9090/repo")
        _ * resolver.resolveUri('repo-dir') >> uri
        transportFactory.createTransport('http', 'repo', _) >> transport()

        and:
        repository.name = 'repo'
        repository.url = 'repo-dir'

        when:
        def repo = repository.createRealResolver()

        then:
        repo instanceof MavenResolver
        repo.root == uri
    }

    def "creates repository with additional artifact URLs"() {
        given:
        def uri = new URI("http://localhost:9090/repo")
        def uri1 = new URI("http://localhost:9090/repo1")
        def uri2 = new URI("http://localhost:9090/repo2")
        _ * resolver.resolveUri('repo-dir') >> uri
        _ * resolver.resolveUri('repo1') >> uri1
        _ * resolver.resolveUri('repo2') >> uri2
        transportFactory.createTransport('http', 'repo', _) >> transport()

        and:
        repository.name = 'repo'
        repository.url = 'repo-dir'
        repository.artifactUrls('repo1', 'repo2')

        when:
        def repo = repository.createRealResolver()

        then:
        repo instanceof MavenResolver
        repo.root == uri
        repo.artifactPatterns.size() == 3
        repo.artifactPatterns.any { it.startsWith uri.toString() }
        repo.artifactPatterns.any { it.startsWith uri1.toString() }
        repo.artifactPatterns.any { it.startsWith uri2.toString() }
    }

    def "creates s3 repository"() {
        given:
        def uri = new URI("s3://localhost:9090/repo")
        _ * resolver.resolveUri(_) >> uri
        transportFactory.createTransport(_, 'repo', _) >> transport()

        and:
        repository.name = 'repo'
        repository.url = 'repo-dir'

        when:
        def repo = repository.createRealResolver()

        then:
        repo instanceof MavenResolver
        repo.root == uri

    }

    def "fails when no root url specified"() {
        when:
        repository.createRealResolver()

        then:
        InvalidUserDataException e = thrown()
        e.message == 'You must specify a URL for a Maven repository.'
    }

    def "create repository from strongly typed URI"() {
        given:
        def uri = new URI("http://localhost:9090/repo")
        _ * resolver.resolveUri(_) >> uri
        transportFactory.createTransport(_, 'repo', _) >> transport()

        and:
        repository.name = 'repo'
        repository.url = uri

        when:
        def repo = repository.createRealResolver()

        then:
        repo instanceof MavenResolver
        repo.root == uri
    }

    private RepositoryTransport transport() {
        return Mock(RepositoryTransport) {
            getRepository() >> resourceRepository
        }
    }
}
