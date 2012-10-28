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
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.externalresource.transport.ExternalResourceRepository
import org.gradle.api.internal.externalresource.transport.file.FileTransport
import org.gradle.api.internal.externalresource.transport.http.HttpTransport
import org.gradle.api.internal.externalresource.cached.CachedExternalResourceIndex
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder
import org.gradle.api.internal.file.FileResolver
import spock.lang.Specification
import org.gradle.logging.ProgressLoggerFactory

class DefaultIvyArtifactRepositoryTest extends Specification {
    final FileResolver fileResolver = Mock()
    final PasswordCredentials credentials = Mock()
    final RepositoryTransportFactory transportFactory = Mock()
    final RepositoryCacheManager cacheManager = Mock()
    final LocallyAvailableResourceFinder locallyAvailableResourceFinder = Mock()
    final CachedExternalResourceIndex cachedExternalResourceIndex = Mock()
    final ProgressLoggerFactory progressLoggerFactory = Mock()
    final DefaultIvyArtifactRepository repository = new DefaultIvyArtifactRepository(
            fileResolver, credentials, transportFactory, locallyAvailableResourceFinder, cachedExternalResourceIndex
    )

    def "cannot create a resolver for url with unknown scheme"() {
        repository.name = 'name'
        repository.artifactPattern 'pattern1'

        given:
        fileResolver.resolveUri('pattern1') >> new URI('scheme:resource1')

        when:
        repository.createResolver()

        then:
        InvalidUserDataException e = thrown()
        e.message == "You may only specify 'file', 'http' and 'https' urls for an ivy repository."
    }

    def "cannot creates a resolver for mixed url scheme"() {
        repository.name = 'name'
        repository.artifactPattern 'pattern1'
        repository.artifactPattern 'pattern2'

        given:
        fileResolver.resolveUri('pattern1') >> new URI('http:resource1')
        fileResolver.resolveUri('pattern2') >> new URI('file:resource2')

        when:
        repository.createResolver()

        then:
        InvalidUserDataException e = thrown()
        e.message == "You cannot mix file and http(s) urls for a single ivy repository. Please declare 2 separate repositories."
    }

    def "creates a resolver for HTTP patterns"() {
        repository.name = 'name'
        repository.artifactPattern 'http://host/[organisation]/[artifact]-[revision].[ext]'
        repository.artifactPattern 'http://other/[module]/[artifact]-[revision].[ext]'
        repository.ivyPattern 'http://host/[module]/ivy-[revision].xml'

        given:
        fileResolver.resolveUri('http://host/') >> new URI('http://host/')
        fileResolver.resolveUri('http://other/') >> new URI('http://other/')
        transportFactory.createHttpTransport('name', credentials) >> createHttpTransport("name", credentials)

        when:
        def resolver = repository.createResolver()

        then:
        resolver instanceof ExternalResourceResolver
        resolver.repository instanceof ExternalResourceRepository
        resolver.name == 'name'
        resolver.artifactPatterns == ['http://host/[organisation]/[artifact]-[revision].[ext]', 'http://other/[module]/[artifact]-[revision].[ext]'] as List
        resolver.ivyPatterns == ['http://host/[module]/ivy-[revision].xml'] as List
    }

    def "creates a resolver for file patterns"() {
        repository.name = 'name'
        repository.artifactPattern 'repo/[organisation]/[artifact]-[revision].[ext]'
        repository.artifactPattern 'repo/[organisation]/[module]/[artifact]-[revision].[ext]'
        repository.ivyPattern 'repo/[organisation]/[module]/ivy-[revision].xml'
        def file = new File("test")
        def fileUri = file.toURI()

        given:
        fileResolver.resolveUri('repo/') >> fileUri
        transportFactory.createFileTransport('name') >> new FileTransport('name', cacheManager)

        when:
        def resolver = repository.createResolver()

        then:
        resolver instanceof ExternalResourceResolver
        resolver.repository instanceof ExternalResourceRepository
        resolver.name == 'name'
        resolver.artifactPatterns == ["${file.absolutePath}/[organisation]/[artifact]-[revision].[ext]", "${file.absolutePath}/[organisation]/[module]/[artifact]-[revision].[ext]"] as List
        resolver.ivyPatterns == ["${file.absolutePath}/[organisation]/[module]/ivy-[revision].xml"] as List
    }

    def "uses gradle patterns with specified url and default layout"() {
        repository.name = 'name'
        repository.url = 'http://host'

        given:
        fileResolver.resolveUri('http://host') >> new URI('http://host/')
        transportFactory.createHttpTransport('name', credentials) >> createHttpTransport("name", credentials)

        when:
        def resolver = repository.createResolver()

        then:
        resolver instanceof ExternalResourceResolver
        resolver.repository instanceof ExternalResourceRepository
        resolver.name == 'name'
        resolver.artifactPatterns == ['http://host/[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])'] as List
        resolver.ivyPatterns == ["http://host/[organisation]/[module]/[revision]/ivy-[revision].xml"] as List
    }

    def "uses maven patterns with specified url and maven layout"() {
        repository.name = 'name'
        repository.url = 'http://host'
        repository.layout 'maven'

        given:
        fileResolver.resolveUri('http://host') >> new URI('http://host/')
        transportFactory.createHttpTransport('name', credentials) >> createHttpTransport("name", credentials)

        when:
        def resolver = repository.createResolver()

        then:
        resolver instanceof ExternalResourceResolver
        resolver.repository instanceof ExternalResourceRepository
        resolver.name == 'name'
        resolver.artifactPatterns == ['http://host/[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])'] as List
        resolver.ivyPatterns == ["http://host/[organisation]/[module]/[revision]/ivy-[revision].xml"] as List
        resolver.m2compatible
    }

    def "uses specified base url with configured pattern layout"() {
        repository.name = 'name'
        repository.url = 'http://host'
        repository.layout 'pattern', {
            artifact '[module]/[revision]/[artifact](.[ext])'
            ivy '[module]/[revision]/ivy.xml'
        }

        given:
        fileResolver.resolveUri('http://host') >> new URI('http://host/')
        transportFactory.createHttpTransport('name', credentials) >> createHttpTransport("name", credentials)

        when:
        def resolver = repository.createResolver()

        then:
        resolver instanceof ExternalResourceResolver
        resolver.repository instanceof ExternalResourceRepository
        resolver.name == 'name'
        resolver.artifactPatterns == ['http://host/[module]/[revision]/[artifact](.[ext])'] as List
        resolver.ivyPatterns == ["http://host/[module]/[revision]/ivy.xml"] as List
    }

    def "combines layout patterns with additionally specified patterns"() {
        repository.name = 'name'
        repository.url = 'http://host/'
        repository.artifactPattern 'http://host/[other]/artifact'
        repository.ivyPattern 'http://host/[other]/ivy'

        given:
        fileResolver.resolveUri('http://host/') >> new URI('http://host/')
        transportFactory.createHttpTransport('name', credentials) >> createHttpTransport("name", credentials)

        when:
        def resolver = repository.createResolver()

        then:
        resolver instanceof ExternalResourceResolver
        resolver.repository instanceof ExternalResourceRepository
        resolver.name == 'name'
        resolver.artifactPatterns == ['http://host/[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])', 'http://host/[other]/artifact'] as List
        resolver.ivyPatterns == ["http://host/[organisation]/[module]/[revision]/ivy-[revision].xml", 'http://host/[other]/ivy'] as List
    }

    def "uses artifact pattern for ivy files when no ivy pattern provided"() {
        repository.name = 'name'
        repository.url = 'http://host'
        repository.layout 'pattern', {
            artifact '[layoutPattern]'
        }
        repository.artifactPattern 'http://other/[additionalPattern]'
        transportFactory.createHttpTransport('name', credentials) >> createHttpTransport("name", credentials)

        given:
        fileResolver.resolveUri('http://host') >> new URI('http://host')
        fileResolver.resolveUri('http://other/') >> new URI('http://other/')

        when:
        def resolver = repository.createResolver()

        then:
        resolver.artifactPatterns == ['http://host/[layoutPattern]', 'http://other/[additionalPattern]'] as List
        resolver.ivyPatterns == resolver.artifactPatterns
    }

    def "fails when no artifact patterns specified"() {
        given:
        transportFactory.createHttpTransport('name', credentials) >> createHttpTransport("name", credentials)

        when:
        repository.createResolver()

        then:
        InvalidUserDataException e = thrown()
        e.message == 'You must specify a base url or at least one artifact pattern for an Ivy repository.'
    }

    private HttpTransport createHttpTransport(String name, PasswordCredentials credentials) {
        return new HttpTransport(name, credentials, cacheManager, progressLoggerFactory)
    }

}
