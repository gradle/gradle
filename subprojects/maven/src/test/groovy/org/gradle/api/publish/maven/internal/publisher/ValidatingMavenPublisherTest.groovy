/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.publish.maven.InvalidMavenPublicationException
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.internal.MavenProjectIdentity
import spock.lang.Specification

public class ValidatingMavenPublisherTest extends Specification {

    def delegate = Mock(MavenPublisher)
    def publisher = new ValidatingMavenPublisher(delegate)

    def "validates project coordinates"() {
        given:
        def projectIdentity = Mock(MavenProjectIdentity)
        projectIdentity.groupId >> groupId
        projectIdentity.artifactId >> artifactId
        projectIdentity.version >> version
        def publication = new MavenNormalizedPublication("pub-name", null, projectIdentity, Mock(MavenArtifact), Collections.emptySet())

        def repository = Mock(MavenArtifactRepository)

        when:
        publisher.publish(publication, repository)

        then:
        def e = thrown InvalidMavenPublicationException
        e.message == message

        where:
        groupId             | artifactId             | version   | message
        ""                  | "artifact"             | "version" | "The groupId value cannot be empty"
        "group"             | ""                     | "version" | "The artifactId value cannot be empty"
        "group"             | "artifact"             | ""        | "The version value cannot be empty"
        "group with spaces" | "artifact"             | "version" | "The groupId value is not a valid Maven identifier ([A-Za-z0-9_\\-.]+)"
        "group-₦ガき∆"        | "artifact"             | "version" | "The groupId value is not a valid Maven identifier ([A-Za-z0-9_\\-.]+)"
        "group"             | "artifact with spaces" | "version" | "The artifactId value is not a valid Maven identifier ([A-Za-z0-9_\\-.]+)"
        "group"             | "artifact-₦ガき∆"        | "version" | "The artifactId value is not a valid Maven identifier ([A-Za-z0-9_\\-.]+)"

    }
}
