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
package org.gradle.api.internal.plugins

import spock.lang.Specification
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.artifacts.PublishArtifact

class DefaultArtifactPublicationSetTest extends Specification {
    final PublishArtifactSet publications = Mock()
    final DefaultArtifactPublicationSet publication = new DefaultArtifactPublicationSet(publications)

    def "adds jar artifact to publication"() {
        def artifact = artifact("jar")

        when:
        publication.addCandidate(artifact)

        then:
        1 * publications.add(artifact)
    }

    def "adds war artifact to publication"() {
        def artifact = artifact("war")

        when:
        publication.addCandidate(artifact)

        then:
        1 * publications.add(artifact)
    }

    def "adds ear artifact to publication"() {
        def artifact = artifact("ear")

        when:
        publication.addCandidate(artifact)

        then:
        1 * publications.add(artifact)
    }

    def "prefers war over jar artifact"() {
        def jar = artifact("jar")
        def war = artifact("war")

        given:
        publication.addCandidate(jar)

        when:
        publication.addCandidate(war)

        then:
        1 * publications.remove(jar)
        1 * publications.add(war)

        when:
        publication.addCandidate(jar)

        then:
        0 * publications._
    }

    def "prefers ear over jar artifact"() {
        def jar = artifact("jar")
        def ear = artifact("ear")

        given:
        publication.addCandidate(jar)

        when:
        publication.addCandidate(ear)

        then:
        1 * publications.remove(jar)
        1 * publications.add(ear)

        when:
        publication.addCandidate(jar)

        then:
        0 * publications._
    }

    def "prefers ear over war artifact"() {
        def war = artifact("war")
        def ear = artifact("ear")

        given:
        publication.addCandidate(war)

        when:
        publication.addCandidate(ear)

        then:
        1 * publications.remove(war)
        1 * publications.add(ear)

        when:
        publication.addCandidate(war)

        then:
        0 * publications._
    }

    def "adds other types of artifacts"() {
        def jar = artifact("jar")
        def exe = artifact("exe")

        given:
        publication.addCandidate(jar)

        when:
        publication.addCandidate(exe)

        then:
        1 * publications.add(exe)
        0 * publications._
    }

    def PublishArtifact artifact(String type) {
        PublishArtifact artifact = Mock()
        _ * artifact.toString() >> type
        _ * artifact.type >> type
        return artifact
    }
}
