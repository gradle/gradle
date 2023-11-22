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

import org.gradle.util.TestUtil
import spock.lang.Specification
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.artifacts.PublishArtifact

@SuppressWarnings("deprecation")
class DefaultArtifactPublicationSetTest extends Specification {
    final PublishArtifactSet publications = Mock()
    final DefaultArtifactPublicationSet publication = TestUtil.newInstance(DefaultArtifactPublicationSet, publications)

    def "adds provider to artifact set"() {
        when:
        publication.addCandidate(artifact(artifactType))

        then:
        1 * publications.addAllLater(_)

        where:
        artifactType << ["jar", "war", "ear", "other"]
    }

    def "adds jar artifact to publication"() {
        def artifact = artifact("jar")

        when:
        publication.addCandidate(artifact)

        then:
        publication.defaultArtifactProvider.get() == set(artifact)
    }

    def "adds war artifact to publication"() {
        def artifact = artifact("war")

        when:
        publication.addCandidate(artifact)

        then:
        publication.defaultArtifactProvider.get() == set(artifact)
    }

    def "adds ear artifact to publication"() {
        def artifact = artifact("ear")

        when:
        publication.addCandidate(artifact)

        then:
        publication.defaultArtifactProvider.get() == set(artifact)
    }

    def "adds other type to publication"() {
        def artifact = artifact("zip")

        when:
        publication.addCandidate(artifact)

        then:
        publication.defaultArtifactProvider.get() == set(artifact)
    }

    def "prefers war over jar artifact"() {
        def jar = artifact("jar")
        def war = artifact("war")

        when:
        publication.addCandidate(jar)

        then:
        publication.defaultArtifactProvider.get() == set(jar)

        when:
        publication.addCandidate(war)

        then:
        publication.defaultArtifactProvider.get() == set(war)

        when:
        publication.addCandidate(jar)

        then:
        publication.defaultArtifactProvider.get() == set(war)
    }

    def "prefers ear over jar artifact"() {
        def jar = artifact("jar")
        def ear = artifact("ear")

        when:
        publication.addCandidate(jar)

        then:
        publication.defaultArtifactProvider.get() == set(jar)

        when:
        publication.addCandidate(ear)

        then:
        publication.defaultArtifactProvider.get() == set(ear)

        when:
        publication.addCandidate(jar)

        then:
        publication.defaultArtifactProvider.get() == set(ear)
    }

    def "prefers ear over war artifact"() {
        def war = artifact("war")
        def ear = artifact("ear")

        when:
        publication.addCandidate(war)

        then:
        publication.defaultArtifactProvider.get() == set(war)

        when:
        publication.addCandidate(ear)

        then:
        publication.defaultArtifactProvider.get() == set(ear)

        when:
        publication.addCandidate(war)

        then:
        publication.defaultArtifactProvider.get() == set(ear)
    }

    def "prefers ear over both jar and war artifacts"() {
        def jar = artifact("jar")
        def war = artifact("war")
        def ear = artifact("ear")

        when:
        publication.addCandidate(jar)

        then:
        publication.defaultArtifactProvider.get() == set(jar)

        when:
        publication.addCandidate(war)

        then:
        publication.defaultArtifactProvider.get() == set(war)

        when:
        publication.addCandidate(ear)

        then:
        publication.defaultArtifactProvider.get() == set(ear)

        when:
        publication.addCandidate(jar)

        then:
        publication.defaultArtifactProvider.get() == set(ear)

        when:
        publication.addCandidate(war)

        then:
        publication.defaultArtifactProvider.get() == set(ear)
    }

    def "always adds other types of artifacts when the default is a #artifactType"() {
        def jarWarEar = artifact(artifactType)
        def exe = artifact("exe")

        when:
        publication.addCandidate(jarWarEar)

        then:
        publication.defaultArtifactProvider.get() == set(jarWarEar)

        when:
        publication.addCandidate(exe)

        then:
        publication.defaultArtifactProvider.get() == set(jarWarEar, exe)

        where:
        artifactType << ["jar", "war", "ear"]
    }

    def "always adds other types of artifacts when the default is not a jar/war/ear"() {
        def zip = artifact("zip")
        def exe = artifact("exe")

        when:
        publication.addCandidate(zip)

        then:
        publication.defaultArtifactProvider.get() == set(zip)

        when:
        publication.addCandidate(exe)

        then:
        publication.defaultArtifactProvider.get() == set(zip, exe)
    }

    def "other artifacts are not removed by jar/war"() {
        def exe = artifact("exe")
        def jar = artifact("jar")
        def war = artifact("war")

        when:
        publication.addCandidate(exe)

        then:
        publication.defaultArtifactProvider.get() == set(exe)

        when:
        publication.addCandidate(jar)

        then:
        publication.defaultArtifactProvider.get() == set(exe)

        when:
        publication.addCandidate(war)

        then:
        publication.defaultArtifactProvider.get() == set(exe)
    }

    def "other artifacts are removed by ear"() {
        def exe = artifact("exe")
        def ear = artifact("ear")

        when:
        publication.addCandidate(exe)

        then:
        publication.defaultArtifactProvider.get() == set(exe)

        when:
        publication.addCandidate(ear)

        then:
        publication.defaultArtifactProvider.get() == set(ear)
    }

    def "current default is cached after realizing the provider"() {
        def jar = Mock(PublishArtifact)
        def war = Mock(PublishArtifact)

        when:
        publication.addCandidate(jar)
        def artifacts = publication.defaultArtifactProvider.get()

        then:
        artifacts == set(jar)
        _ * jar.type >> "jar"

        when:
        publication.addCandidate(war)
        artifacts = publication.defaultArtifactProvider.get()

        then:
        artifacts == set(war)
        _ * jar.type >> "jar"
        _ * war.type >> "war"

        when:
        artifacts = publication.defaultArtifactProvider.get()

        then:
        artifacts == set(war)

        and:
        0 * _
    }

    def PublishArtifact artifact(String type) {
        PublishArtifact artifact = Stub() {
            toString() >> type
            getType() >> type
        }
        return artifact
    }

    Set<PublishArtifact> set(PublishArtifact... artifacts) {
        return artifacts as Set
    }
}
