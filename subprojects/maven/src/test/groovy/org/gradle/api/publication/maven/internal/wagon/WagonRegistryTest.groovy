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
import org.apache.maven.artifact.manager.WagonManager
import org.codehaus.plexus.PlexusContainer
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import spock.lang.Specification

class WagonRegistryTest extends Specification {


    public static final String S3 = "s3"

    def "should contain an S3 wagon"() {
        PlexusContainer plexusContainer = Mock()
        expect:
        new WagonRegistry(plexusContainer).protocols.contains('s3')
    }

    def "should register a wagon for the S3 protocol"() {
        given:
        RepositoryTransportDeployWagon wagon = Mock()

        WagonManager wagonManager = Mock()
        wagonManager.getWagon(wagonProtocol) >> wagon

        PlexusContainer container = Mock()
        container.lookup(WagonManager.ROLE) >> wagonManager

        MavenArtifactRepository artifactRepository = Mock()
        artifactRepository.getUrl() >> new URI(artifactUri)

        WagonRegistry registry = new WagonRegistry(container)
        when:
        registry.registerAll()

        then:
        1 * container.addComponentDescriptor(_)

        where:
        artifactUri     | wagonProtocol
        "s3://somerepo" | 's3'
        "S3://somerepo" | 's3'
    }

    def "should create a deploy delegate when preparing for publication"() {
        given:
        RepositoryTransportDeployWagon wagon = Mock()
        RepositoryTransportFactory repositoryTransportFactory = Mock()
        WagonManager wagonManager = Mock()
        wagonManager.getWagon(S3) >> wagon

        PlexusContainer container = Mock()
        container.lookup(WagonManager.ROLE) >> wagonManager

        MavenArtifactRepository artifactRepository = Mock()
        artifactRepository.getUrl() >> new URI("s3://somerepo")

        when:
        WagonRegistry registry = new WagonRegistry(container)
        registry.prepareForPublish(artifactRepository, repositoryTransportFactory)

        then:
        1 * wagon.createDelegate(S3, artifactRepository, repositoryTransportFactory)
    }

    def "should not create a deploy delegate when protocols do not match"() {
        given:
        RepositoryTransportDeployWagon wagon = Mock()
        RepositoryTransportFactory repositoryTransportFactory = Mock()
        WagonManager wagonManager = Mock()
        wagonManager.getWagon(S3) >> wagon
        PlexusContainer container = Mock()
        container.lookup(WagonManager.ROLE) >> wagonManager

        MavenArtifactRepository artifactRepository = Mock()
        artifactRepository.getUrl() >> new URI('testScheme://somerepo')

        WagonRegistry registry = new WagonRegistry(container)

        when:
        registry.registerAll()
        registry.prepareForPublish(artifactRepository, Mock(RepositoryTransportFactory))

        then:
        1 * container.addComponentDescriptor(_)
        0 * wagon.createDelegate(S3, artifactRepository, repositoryTransportFactory)
    }
}
