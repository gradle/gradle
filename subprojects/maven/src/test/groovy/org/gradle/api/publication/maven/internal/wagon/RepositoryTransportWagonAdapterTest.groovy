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

package org.gradle.api.publication.maven.internal.wagon

import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.internal.artifacts.repositories.MavenArtifactRepositoryInternal
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.transport.ExternalResourceRepository
import spock.lang.Specification
import spock.lang.Unroll

class RepositoryTransportWagonAdapterTest extends Specification {

    public static final URI S3_URI = new URI("s3://somewhere/maven")

    @Unroll
    def "should determine the correct remote resource uri"() {
        given:
        MavenArtifactRepositoryInternal mavenArtifactRepository = Mock()
        mavenArtifactRepository.getUrl() >> repoUrl
        RepositoryTransportFactory repositoryTransportFactory = Mock()
        RepositoryTransport repositoryTransport = Mock()
        ExternalResourceRepository externalResourceRepo = Mock()
        repositoryTransport.getRepository() >> externalResourceRepo
        repositoryTransportFactory.createTransport(*_) >> repositoryTransport

        RepositoryTransportWagonAdapter delegate = new RepositoryTransportWagonAdapter("s3", mavenArtifactRepository, repositoryTransportFactory)

        when:
        delegate.getRemoteFile(null, resourceName)

        then:
        1 * repositoryTransport.getRepository().getResource({ it.toString() == expected })
        where:
        repoUrl                          | resourceName    | expected
        S3_URI                           | 'a/b/some.jar'  | 's3://somewhere/maven/a/b/some.jar'
        new URI("s3://somewhere/maven/") | 'a/b/some.jar'  | 's3://somewhere/maven/a/b/some.jar'
        S3_URI                           | '/a/b/some.jar' | 's3://somewhere/maven/a/b/some.jar'
    }

    def "returns true when remote resource was retrieved and written"() {
        given:
        MavenArtifactRepositoryInternal mavenArtifactRepository = Mock()
        mavenArtifactRepository.getUrl() >> S3_URI
        RepositoryTransportFactory repositoryTransportFactory = Mock()
        RepositoryTransport repositoryTransport = Mock()
        ExternalResourceRepository externalResourceRepo = Mock()

        repositoryTransport.getRepository() >> externalResourceRepo
        externalResourceRepo.getResource(_) >> Mock(ExternalResource)
        repositoryTransportFactory.createTransport(*_) >> repositoryTransport

        RepositoryTransportWagonAdapter delegate = new RepositoryTransportWagonAdapter("s3", mavenArtifactRepository, repositoryTransportFactory)

        expect:
        delegate.getRemoteFile(null, 'a/b/some.jar')
    }

    def "returns false when the remote resource does not exist"() {
        given:
        MavenArtifactRepositoryInternal mavenArtifactRepository = Mock()
        mavenArtifactRepository.getUrl() >> S3_URI
        RepositoryTransportFactory repositoryTransportFactory = Mock()
        RepositoryTransport repositoryTransport = Mock()
        ExternalResourceRepository externalResourceRepo = Mock()

        repositoryTransport.getRepository() >> externalResourceRepo
        externalResourceRepo.getResource(_) >> null
        repositoryTransportFactory.createTransport(*_) >> repositoryTransport

        RepositoryTransportWagonAdapter delegate = new RepositoryTransportWagonAdapter("s3", mavenArtifactRepository, repositoryTransportFactory)

        expect:
        !delegate.getRemoteFile(null, 'a/b/some.jar')
    }

    def "should put a file to the correct uri"() {
        given:
        MavenArtifactRepositoryInternal mavenArtifactRepository = Mock()
        mavenArtifactRepository.getUrl() >> S3_URI
        RepositoryTransportFactory repositoryTransportFactory = Mock()
        RepositoryTransport repositoryTransport = Mock()
        ExternalResourceRepository externalResourceRepo = Mock()
        repositoryTransport.getRepository() >> externalResourceRepo
        externalResourceRepo.withProgressLogging() >> externalResourceRepo
        repositoryTransportFactory.createTransport(*_) >> repositoryTransport

        RepositoryTransportWagonAdapter delegate = new RepositoryTransportWagonAdapter("s3", mavenArtifactRepository, repositoryTransportFactory)

        when:
        delegate.putRemoteFile(null, 'something.jar')

        then:
        1 * externalResourceRepo.put(null, { it.toString() == 's3://somewhere/maven/something.jar'})
    }
}
