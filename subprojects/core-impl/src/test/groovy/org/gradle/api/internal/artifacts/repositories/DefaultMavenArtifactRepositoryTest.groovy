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

import org.apache.ivy.core.cache.RepositoryCacheManager
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.internal.artifacts.ivyservice.filestore.ExternalArtifactCache
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.artifacts.repositories.transport.file.FileTransport
import org.gradle.api.internal.artifacts.repositories.transport.http.HttpTransport
import org.gradle.api.internal.file.FileResolver
import spock.lang.Specification

class DefaultMavenArtifactRepositoryTest extends Specification {
    final FileResolver resolver = Mock()
    final PasswordCredentials credentials = Mock()
    final RepositoryTransportFactory transportFactory = Mock()
    final RepositoryCacheManager cacheManager = Mock()
    final DefaultMavenArtifactRepository repository = new DefaultMavenArtifactRepository(resolver, credentials, transportFactory)

    def "creates local repository"() {
        given:
        def file = new File('repo')
        def uri = file.toURI()
        _ * resolver.resolveUri('repo-dir') >> uri
        transportFactory.createFileTransport('repo') >> new FileTransport('repo', cacheManager)

        and:
        repository.name = 'repo'
        repository.url = 'repo-dir'

        when:
        def repo = repository.createResolver()

        then:
        repo instanceof MavenResolver
        repo.root == "${file.absolutePath}/"
    }

    def "creates http repository"() {
        given:
        def uri = new URI("http://localhost:9090/repo")
        _ * resolver.resolveUri('repo-dir') >> uri
        2 * credentials.getUsername() >> 'username'
        1 * credentials.getPassword() >> 'password'
        transportFactory.createHttpTransport('repo', credentials) >> new HttpTransport('repo', credentials, Mock(ExternalArtifactCache), cacheManager)
        cacheManager.name >> 'cache'
        0 * _._

        and:
        repository.name = 'repo'
        repository.url = 'repo-dir'

        when:
        def repo = repository.createResolver()

        then:
        repo instanceof MavenResolver
        repo.root == "${uri}/"
    }

    def "creates repository with additional artifact URLs"() {
        given:
        def uri = new URI("http://localhost:9090/repo")
        def uri1 = new URI("http://localhost:9090/repo1")
        def uri2 = new URI("http://localhost:9090/repo2")
        _ * resolver.resolveUri('repo-dir') >> uri
        _ * resolver.resolveUri('repo1') >> uri1
        _ * resolver.resolveUri('repo2') >> uri2
        transportFactory.createHttpTransport('repo', credentials) >> new HttpTransport('repo', credentials, Mock(ExternalArtifactCache), cacheManager)

        and:
        repository.name = 'repo'
        repository.url = 'repo-dir'
        repository.artifactUrls('repo1', 'repo2')

        when:
        def repo = repository.createResolver()

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
        repository.createResolver()

        then:
        InvalidUserDataException e = thrown()
        e.message == 'You must specify a URL for a Maven repository.'
    }
}
