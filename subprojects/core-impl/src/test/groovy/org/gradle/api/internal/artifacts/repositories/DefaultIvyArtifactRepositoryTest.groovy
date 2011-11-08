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

import spock.lang.Specification
import org.apache.ivy.plugins.resolver.URLResolver
import org.apache.ivy.plugins.resolver.RepositoryResolver
import org.gradle.api.internal.file.FileResolver
import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.repositories.PasswordCredentials

class DefaultIvyArtifactRepositoryTest extends Specification {
    final FileResolver fileResolver = Mock()
    final PasswordCredentials credentials = Mock()
    final DefaultIvyArtifactRepository repository = new DefaultIvyArtifactRepository(fileResolver, credentials)

    def "creates a resolver for URL patterns"() {
        repository.name = 'name'
        repository.artifactPattern 'pattern1'
        repository.artifactPattern 'pattern2'
        repository.ivyPattern 'ivyPattern'

        given:
        fileResolver.resolveUri('pattern1') >> new URI('scheme:resource1')
        fileResolver.resolveUri('pattern2') >> new URI('scheme:resource2')
        fileResolver.resolveUri('ivyPattern') >> new URI('scheme:ivyFile')

        when:
        def resolvers = []
        repository.createResolvers(resolvers)

        then:
        resolvers.size() == 1
        def resolver = resolvers[0]
        resolver instanceof URLResolver
        resolver.name == 'name'
        resolver.artifactPatterns == ['scheme:resource1', 'scheme:resource2'] as List
        resolver.ivyPatterns == ['scheme:ivyFile'] as List
    }

    def "creates a resolver for HTTP patterns"() {
        repository.name = 'name'
        repository.artifactPattern 'http://host/[organisation]/[artifact]-[revision].[ext]'
        repository.artifactPattern 'http://other/[module]/[artifact]-[revision].[ext]'
        repository.ivyPattern 'http://host/[module]/ivy-[revision].xml'

        given:
        fileResolver.resolveUri('http://host/') >> new URI('http://host/')
        fileResolver.resolveUri('http://other/') >> new URI('http://other/')

        when:
        def resolvers = []
        repository.createResolvers(resolvers)

        then:
        resolvers.size() == 1
        def resolver = resolvers[0]
        resolver instanceof RepositoryResolver
        resolver.repository instanceof CommonsHttpClientBackedRepository
        resolver.name == 'name'
        resolver.artifactPatterns == ['http://host/[organisation]/[artifact]-[revision].[ext]', 'http://other/[module]/[artifact]-[revision].[ext]'] as List
        resolver.ivyPatterns == ['http://host/[module]/ivy-[revision].xml'] as List
    }

    def "creates a resolver for file patterns"() {
        repository.name = 'name'
        repository.artifactPattern 'repo/[organisation]/[artifact]-[revision].[ext]'
        repository.artifactPattern 'repo/[organisation]/[module]/[artifact]-[revision].[ext]'
        repository.ivyPattern 'repo/[organisation]/[module]/ivy-[revision].xml'
        def file = new File("test").toURI()

        given:
        fileResolver.resolveUri('repo/') >> file

        when:
        def resolvers = []
        repository.createResolvers(resolvers)

        then:
        resolvers.size() == 1
        def resolver = resolvers[0]
        resolver instanceof FileSystemResolver
        resolver.name == 'name'
        resolver.artifactPatterns == ["${file.path}/[organisation]/[artifact]-[revision].[ext]", "${file.path}/[organisation]/[module]/[artifact]-[revision].[ext]"] as List
        resolver.ivyPatterns == ["${file.path}/[organisation]/[module]/ivy-[revision].xml"] as List
    }

    def "creates a URL resolver for mixed patterns"() {
        repository.name = 'name'
        repository.artifactPattern 'http://host/[module]/[artifact]-[revision].[ext]'
        repository.artifactPattern 'repo/[organisation]/[artifact]-[revision].[ext]'
        def file = new File("test").toURI()

        given:
        fileResolver.resolveUri('http://host/') >> new URI('http://host/')
        fileResolver.resolveUri('repo/') >> file

        when:
        def resolvers = []
        repository.createResolvers(resolvers)

        then:
        resolvers.size() == 1
        def resolver = resolvers[0]
        resolver instanceof URLResolver
        resolver.name == 'name'
        resolver.artifactPatterns == ['http://host/[module]/[artifact]-[revision].[ext]', "file:${file.path}/[organisation]/[artifact]-[revision].[ext]"] as List
    }

    def "uses gradle patterns with specified url and default layout"() {
        repository.name = 'name'
        repository.url = 'http://host'

        given:
        fileResolver.resolveUri('http://host') >> new URI('http://host/')

        when:
        def resolvers = []
        repository.createResolvers(resolvers)

        then:
        resolvers.size() == 1
        def resolver = resolvers[0]
        resolver instanceof RepositoryResolver
        resolver.repository instanceof CommonsHttpClientBackedRepository
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

        when:
        def resolvers = []
        repository.createResolvers(resolvers)

        then:
        resolvers.size() == 1
        def resolver = resolvers[0]
        resolver instanceof RepositoryResolver
        resolver.repository instanceof CommonsHttpClientBackedRepository
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

        when:
        def resolvers = []
        repository.createResolvers(resolvers)

        then:
        resolvers.size() == 1
        def resolver = resolvers[0]
        resolver instanceof RepositoryResolver
        resolver.repository instanceof CommonsHttpClientBackedRepository
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

        when:
        def resolvers = []
        repository.createResolvers(resolvers)

        then:
        resolvers.size() == 1
        def resolver = resolvers[0]
        resolver instanceof RepositoryResolver
        resolver.repository instanceof CommonsHttpClientBackedRepository
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

        given:
        fileResolver.resolveUri('http://host') >> new URI('http://host')
        fileResolver.resolveUri('http://other/') >> new URI('http://other/')

        when:
        def resolvers = []
        repository.createResolvers(resolvers)

        then:
        resolvers.size() == 1
        def resolver = resolvers[0]
        resolver.artifactPatterns == ['http://host/[layoutPattern]', 'http://other/[additionalPattern]'] as List
        resolver.ivyPatterns == resolver.artifactPatterns
    }

    def "fails when no artifact patterns specified"() {
        when:
        repository.createResolvers([])

        then:
        InvalidUserDataException e = thrown()
        e.message == 'You must specify a base url or at least one artifact pattern for an Ivy repository.'
    }
}
