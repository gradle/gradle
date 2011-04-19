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

class DefaultIvyArtifactRepositoryTest extends Specification {
    final DefaultIvyArtifactRepository repository = new DefaultIvyArtifactRepository()

    def createsAUrlResolver() {
        repository.name = 'name'
        repository.artifactPattern 'pattern'

        when:
        def resolvers = []
        repository.createResolvers(resolvers)

        then:
        resolvers.size() == 1
        def resolver = resolvers[0]
        resolver instanceof URLResolver
        resolver.name == 'name'
        resolver.artifactPatterns == ['pattern'] as List
    }

    def createsARepositoryResolverForHttpPatterns() {
        repository.name = 'name'
        repository.artifactPattern 'http://host/[organisation]/[artifact]-[revision].[ext]'

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
}
