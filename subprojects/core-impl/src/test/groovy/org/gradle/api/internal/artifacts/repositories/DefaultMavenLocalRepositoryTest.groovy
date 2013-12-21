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
package org.gradle.api.internal.artifacts.repositories

import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher
import org.gradle.api.internal.artifacts.repositories.resolver.MavenLocalResolver
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder
import org.gradle.api.internal.externalresource.transport.ExternalResourceRepository
import org.gradle.api.internal.file.FileResolver
import org.gradle.logging.ProgressLoggerFactory
import spock.lang.Specification

class DefaultMavenLocalRepositoryTest extends Specification {
    final FileResolver resolver = Mock()
    final PasswordCredentials credentials = Mock()
    final RepositoryTransportFactory transportFactory = Mock()
    final LocallyAvailableResourceFinder locallyAvailableResourceFinder = Mock()
    final ExternalResourceRepository resourceRepository = Mock()
    final ModuleMetadataProcessor metadataProcessor = Mock()
    final VersionMatcher versionMatcher = Mock()
    final LatestStrategy latestStrategy = Mock()
    final ResolverStrategy resolverStrategy = Stub()

    final DefaultMavenArtifactRepository repository = new DefaultMavenLocalArtifactRepository(
            resolver, credentials, transportFactory, locallyAvailableResourceFinder, metadataProcessor, versionMatcher, latestStrategy, resolverStrategy)
    final ProgressLoggerFactory progressLoggerFactory = Mock()

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
        repo instanceof MavenLocalResolver
        repo.root == "${uri}/"
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
