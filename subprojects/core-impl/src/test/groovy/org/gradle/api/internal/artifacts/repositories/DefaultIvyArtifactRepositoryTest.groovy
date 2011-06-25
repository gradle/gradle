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

class DefaultIvyArtifactRepositoryTest extends Specification {
    final FileResolver fileResolver = Mock()
    final DefaultIvyArtifactRepository repository = new DefaultIvyArtifactRepository(fileResolver)

    def createsAUrlResolver() {
        repository.name = 'name'
        repository.artifactPattern 'pattern'

        given:
        fileResolver.resolveUri('pattern') >> new URI('scheme:resource')

        when:
        def resolvers = []
        repository.createResolvers(resolvers)

        then:
        resolvers.size() == 1
        def resolver = resolvers[0]
        resolver instanceof URLResolver
        resolver.name == 'name'
        resolver.artifactPatterns == ['scheme:resource'] as List
    }

    def createsARepositoryResolverForHttpPatterns() {
        repository.name = 'name'
        repository.artifactPattern 'http://host/[organisation]/[artifact]-[revision].[ext]'

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
        resolver.artifactPatterns == ['http://host/[organisation]/[artifact]-[revision].[ext]'] as List
    }

    def createsARepositoryResolverForFilePattern() {
        repository.name = 'name'
        repository.artifactPattern 'repo/[organisation]/[artifact]-[revision].[ext]'
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
        resolver.artifactPatterns == ["${file.path}/[organisation]/[artifact]-[revision].[ext]"] as List
    }
}
