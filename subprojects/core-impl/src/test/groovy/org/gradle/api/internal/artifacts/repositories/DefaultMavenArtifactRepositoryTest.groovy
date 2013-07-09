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
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ExternalResourceResolverAdapter
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.api.internal.artifacts.repositories.resolver.MavenResolver
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder
import org.gradle.api.internal.externalresource.transport.ExternalResourceRepository
import org.gradle.api.internal.file.FileResolver
import org.gradle.logging.ProgressLoggerFactory
import spock.lang.Specification

class DefaultMavenArtifactRepositoryTest extends Specification {
    final FileResolver resolver = Mock()
    final PasswordCredentials credentials = Mock()
    final RepositoryTransportFactory transportFactory = Mock()
    final LocallyAvailableResourceFinder locallyAvailableResourceFinder = Mock()
    final ExternalResourceRepository resourceRepository = Mock()
    final MetaDataParser metaDataParser = Mock()

    final DefaultMavenArtifactRepository repository = new DefaultMavenArtifactRepository(resolver, credentials, transportFactory, locallyAvailableResourceFinder, metaDataParser)
    final ProgressLoggerFactory progressLoggerFactory = Mock();

    def "creates local repository"() {
        given:
        def file = new File('repo')
        def uri = file.toURI()
        _ * resolver.resolveUri('repo-dir') >> uri
        transportFactory.createFileTransport('repo') >> transport()

        and:
        repository.name = 'repo'
        repository.url = 'repo-dir'

        when:
        def repo = repository.createRealResolver()

        then:
        repo instanceof MavenResolver
        repo.root == "${uri}/"
    }

    def "creates http repository"() {
        given:
        def uri = new URI("http://localhost:9090/repo")
        _ * resolver.resolveUri('repo-dir') >> uri
        _ * credentials.getUsername() >> 'username'
        _ * credentials.getPassword() >> 'password'
        transportFactory.createHttpTransport('repo', credentials) >> transport()

        and:
        repository.name = 'repo'
        repository.url = 'repo-dir'

        when:
        def repo = repository.createRealResolver()

        then:
        repo instanceof MavenResolver
        repo.root == "${uri}/"
    }

    def "creates a DSL wrapper for the repository"() {
        given:
        def file = new File('repo')
        def uri = file.toURI()
        _ * this.resolver.resolveUri('repo-dir') >> uri
        transportFactory.createFileTransport('repo') >> transport()

        and:
        repository.name = 'repo'
        repository.url = 'repo-dir'

        when:
        def resolver = repository.createLegacyDslObject()

        then:
        resolver instanceof LegacyMavenResolver
        resolver.resolver instanceof MavenResolver

        when:
        def repo = resolver.createResolver()

        then:
        repo instanceof ExternalResourceResolverAdapter
        repo.resolver.is(resolver.resolver)
    }

    def "creates repository with additional artifact URLs"() {
        given:
        def uri = new URI("http://localhost:9090/repo")
        def uri1 = new URI("http://localhost:9090/repo1")
        def uri2 = new URI("http://localhost:9090/repo2")
        _ * resolver.resolveUri('repo-dir') >> uri
        _ * resolver.resolveUri('repo1') >> uri1
        _ * resolver.resolveUri('repo2') >> uri2
        transportFactory.createHttpTransport('repo', credentials) >> transport()

        and:
        repository.name = 'repo'
        repository.url = 'repo-dir'
        repository.artifactUrls('repo1', 'repo2')

        when:
        def repo = repository.createRealResolver()

        then:
        repo instanceof MavenResolver
        repo.root == "${uri}/"
        repo.artifactPatterns.size() == 3
        repo.artifactPatterns.any { it.startsWith uri.toString() }
        repo.artifactPatterns.any { it.startsWith uri1.toString() }
        repo.artifactPatterns.any { it.startsWith uri2.toString() }
    }

    def "fails when no root url specified"() {
        when:
        repository.createLegacyDslObject()

        then:
        InvalidUserDataException e = thrown()
        e.message == 'You must specify a URL for a Maven repository.'
    }

    private RepositoryTransport transport() {
        return Mock(RepositoryTransport) {
            getRepository() >> resourceRepository
            convertToPath(_) >> { URI uri ->
                def result = uri.toString()
                return result.endsWith('/') ? result : result + '/'
            }
        }
    }

}
