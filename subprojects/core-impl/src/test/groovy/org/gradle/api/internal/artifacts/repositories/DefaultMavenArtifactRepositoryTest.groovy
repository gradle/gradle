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
        result[0] instanceof IBiblioResolver
        result[0].root == "${file.absolutePath}${File.separatorChar}"
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
        result[0] instanceof IBiblioWharfResolver
        result[0].root == "${uri}/"
    }
}
