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

package org.gradle.api.publication.maven.internal.ant

import org.apache.maven.artifact.manager.WagonManager
import org.codehaus.plexus.PlexusContainer
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.publication.maven.internal.RepositoryTransportDeployWagon
import spock.lang.Specification

class WagonRegistryTest extends Specification {

    def "should contain an S3 wagon"() {
        expect:
        new WagonRegistry().wagonDeployments.contains('s3')
    }

    def "should create the deploy delegate for S3"() {
        given:
        RepositoryTransportDeployWagon wagon = Mock()

        WagonManager wagonManager = Mock()
        wagonManager.getWagon(wagonProtocol) >> wagon

        PlexusContainer container = Mock()
        container.lookup(WagonManager.ROLE) >> wagonManager

        MavenDeploy mavenDeploy = Mock()
        mavenDeploy.getContainer() >> container

        MavenArtifactRepository artifactRepository = Mock()
        artifactRepository.getUrl() >> new URI(artifactUri)

        WagonRegistry registry = new WagonRegistry()
        when:
        registry.register(mavenDeploy, artifactRepository, Mock(RepositoryTransportFactory))

        then:
        1 * wagon.createDelegate(wagonProtocol, artifactRepository, _)

        where:
        artifactUri     | wagonProtocol
        "s3://somerepo" | 's3'
        "S3://somerepo" | 's3'
    }

    def "should not create a deploy delegate when protocols do not match"() {
        given:
        PlexusContainer container = Mock()
        container.lookup(WagonManager.ROLE) >> { throw new RuntimeException("should not happen") }

        MavenDeploy mavenDeploy = Mock()
        mavenDeploy.getContainer() >> container

        MavenArtifactRepository artifactRepository = Mock()
        artifactRepository.getUrl() >> new URI('testScheme://somerepo')

        WagonRegistry registry = new WagonRegistry()

        expect:
        registry.register(mavenDeploy, artifactRepository, Mock(RepositoryTransportFactory))
    }
}
