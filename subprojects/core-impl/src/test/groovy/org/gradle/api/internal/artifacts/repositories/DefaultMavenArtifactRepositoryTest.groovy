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
import org.gradle.api.internal.file.FileResolver
import org.apache.ivy.plugins.resolver.IBiblioResolver
import org.jfrog.wharf.ivy.resolver.IBiblioWharfResolver
import org.apache.ivy.plugins.resolver.DualResolver
import org.apache.ivy.plugins.resolver.URLResolver
import org.gradle.api.InvalidUserDataException

class DefaultMavenArtifactRepositoryTest extends Specification {
    final FileResolver resolver = Mock()
    final DefaultMavenArtifactRepository repository = new DefaultMavenArtifactRepository(resolver)

    def "creates local repository"() {
        given:
        def file = new File('repo')
        def uri = file.toURI()
        _ * resolver.resolveUri('repo-dir') >> uri

        and:
        repository.name = 'repo'
        repository.url = 'repo-dir'

        when:
        def result = []
        repository.createResolvers(result)

        then:
        result.size() == 1
        def repo = result[0]
        repo instanceof IBiblioResolver
        repo.root == "${file.absolutePath}/"
    }

    def "creates http repository"() {
        given:
        def uri = new URI("http://localhost:9090/repo")
        _ * resolver.resolveUri('repo-dir') >> uri

        and:
        repository.name = 'repo'
        repository.url = 'repo-dir'

        when:
        def result = []
        repository.createResolvers(result)

        then:
        result.size() == 1
        def repo = result[0]
        repo instanceof IBiblioWharfResolver
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

        and:
        repository.name = 'repo'
        repository.url = 'repo-dir'
        repository.artifactUrls('repo1', 'repo2')

        when:
        def result = []
        repository.createResolvers(result)

        then:
        result.size() == 1
        def repo = result[0]
        repo instanceof DualResolver
        repo.ivyResolver instanceof IBiblioWharfResolver
        repo.ivyResolver.root == "${uri}/"
        repo.artifactResolver instanceof URLResolver
        repo.artifactResolver.artifactPatterns.size() == 3
        repo.artifactResolver.artifactPatterns.any { it.startsWith uri.toString() }
        repo.artifactResolver.artifactPatterns.any { it.startsWith uri1.toString() }
        repo.artifactResolver.artifactPatterns.any { it.startsWith uri2.toString() }
    }

    def "fails when no root url specified"() {
        when:
        repository.createResolvers([])

        then:
        InvalidUserDataException e = thrown()
        e.message == 'You must specify a URL for a Maven repository.'
    }
}
