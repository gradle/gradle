/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.publish.maven.internal.publisher
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.artifacts.repositories.DefaultPasswordCredentials
import org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal
import spock.lang.Specification

class MavenRemoteRepositoryFactoryTest extends Specification {

    def "fails to create maven repository when non-password credentials are specified"() {
        setup:
        MavenArtifactRepositoryInternal mavenArtifactRepository = Mock()
        def someUrl = "http://localhost/somewhere"
        mavenArtifactRepository.getUrl() >> new URI(someUrl)
        mavenArtifactRepository.getCredentials() >> {
            throw new ClassCastException()
        }
        MavenRemoteRepositoryFactory mavenRemoteRepositoryFactory = new MavenRemoteRepositoryFactory(mavenArtifactRepository)

        when:
        mavenRemoteRepositoryFactory.create()

        then:
        thrown ClassCastException
    }

    def "should set authentication when password or username are specified"() {
        setup:
        MavenArtifactRepositoryInternal mavenArtifactRepository = Mock()
        def someUrl = "http://localhost/somewhere"
        mavenArtifactRepository.getUrl() >> new URI(someUrl)
        mavenArtifactRepository.getCredentials() >> new DefaultPasswordCredentials(username, password)
        MavenRemoteRepositoryFactory mavenRemoteRepositoryFactory = new MavenRemoteRepositoryFactory(mavenArtifactRepository)

        when:
        def createdRepo = mavenRemoteRepositoryFactory.create()

        then:
        createdRepo.authentication.userName == username
        createdRepo.authentication.password == password

        and:
        createdRepo.getUrl() == someUrl

        where:
        username | password
        ''       | ''
        null     | ''
        ''       | null
    }

    def "should not set authentication when password and username are null"() {
        setup:
        MavenArtifactRepositoryInternal mavenArtifactRepository = Mock()
        def someUrl = "http://localhost/somewhere"
        mavenArtifactRepository.getUrl() >> new URI(someUrl)
        mavenArtifactRepository.getAlternativeCredentials() >> null
        mavenArtifactRepository.getCredentials() >> new DefaultPasswordCredentials(null, null)
        MavenRemoteRepositoryFactory mavenRemoteRepositoryFactory = new MavenRemoteRepositoryFactory(mavenArtifactRepository)

        when:
        def createdRepo = mavenRemoteRepositoryFactory.create()

        then:
        createdRepo.authentication == null

        and:
        createdRepo.getUrl() == someUrl
    }

    private interface MavenArtifactRepositoryInternal extends MavenArtifactRepository, AuthenticationSupportedInternal {}
}
