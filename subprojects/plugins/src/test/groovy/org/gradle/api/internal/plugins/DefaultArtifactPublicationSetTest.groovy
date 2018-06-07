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
        publication.defaultArtifactProvider.get() == artifact
    }

    def "adds war artifact to publication"() {
        def artifact = artifact("war")

        when:
        publication.addCandidate(artifact)

        then:
        publication.defaultArtifactProvider.get() == artifact
    }

    def "adds ear artifact to publication"() {
        def artifact = artifact("ear")

        when:
        publication.addCandidate(artifact)

        then:
        publication.defaultArtifactProvider.get() == artifact
    }

    def "adds other type to publication"() {
        def artifact = artifact("zip")

        when:
        publication.addCandidate(artifact)

        then:
        publication.defaultArtifactProvider.get() == artifact
    }

    def "prefers war over jar artifact"() {
        def jar = artifact("jar")
        def war = artifact("war")

        when:
        publication.addCandidate(jar)

        then:
        publication.defaultArtifactProvider.get() == jar

        when:
        publication.addCandidate(war)
        def defaultArtifact = publication.defaultArtifactProvider.get()

        then:
        defaultArtifact == war
        1 * publications.remove(jar)
        1 * publications.add(war)

        when:
        publication.addCandidate(jar)

        then:
        publication.defaultArtifactProvider.get() == war
    }

    def "prefers ear over jar artifact"() {
        def jar = artifact("jar")
        def ear = artifact("ear")

        when:
        publication.addCandidate(jar)

        then:
        publication.defaultArtifactProvider.get() == jar

        when:
        publication.addCandidate(ear)
        def defaultArtifact = publication.defaultArtifactProvider.get()

        then:
        defaultArtifact == ear
        1 * publications.remove(jar)
        1 * publications.add(ear)

        when:
        publication.addCandidate(jar)

        then:
        publication.defaultArtifactProvider.get() == ear
    }

    def "prefers ear over war artifact"() {
        def war = artifact("war")
        def ear = artifact("ear")

        when:
        publication.addCandidate(war)

        then:
        publication.defaultArtifactProvider.get() == war

        when:
        publication.addCandidate(ear)
        def defaultArtifact = publication.defaultArtifactProvider.get()

        then:
        defaultArtifact == ear
        1 * publications.remove(war)
        1 * publications.add(ear)

        when:
        publication.addCandidate(war)

        then:
        publication.defaultArtifactProvider.get() == ear
    }

    def "always adds other types of artifacts when the default is a jar/war/ear"() {
        def jar = artifact("jar")
        def exe = artifact("exe")

        when:
        publication.addCandidate(jar)

        then:
        publication.defaultArtifactProvider.get() == jar

        when:
        publication.addCandidate(exe)
        def defaultArtifact = publication.defaultArtifactProvider.get()

        then:
        defaultArtifact == jar
        1 * publications.add(jar)
        1 * publications.add(exe)
    }

    def "always adds other types of artifacts when the default is not a jar/war/ear"() {
        def zip = artifact("zip")
        def exe = artifact("exe")

        when:
        publication.addCandidate(zip)

        then:
        publication.defaultArtifactProvider.get() == zip

        when:
        publication.addCandidate(exe)
        def defaultArtifact = publication.defaultArtifactProvider.get()

        then:
        defaultArtifact == zip
        1 * publications.add(zip)
        1 * publications.add(exe)
    }

    def "other artifacts are not removed by jar/war"() {
        def exe = artifact("exe")
        def jar = artifact("jar")
        def war = artifact("war")

        when:
        publication.addCandidate(exe)

        then:
        publication.defaultArtifactProvider.get() == exe

        when:
        publication.addCandidate(jar)

        then:
        publication.defaultArtifactProvider.get() == exe

        then:
        0 * publications.remove(exe)

        when:
        publication.addCandidate(war)

        then:
        publication.defaultArtifactProvider.get() == exe

        then:
        0 * publications.remove(exe)
    }

    def "other artifacts are removed by ear"() {
        def exe = artifact("exe")
        def ear = artifact("ear")

        when:
        publication.addCandidate(exe)
        def defaultArtifact = publication.defaultArtifactProvider.get()

        then:
        defaultArtifact == exe

        when:
        publication.addCandidate(ear)
        defaultArtifact = publication.defaultArtifactProvider.get()

        then:
        defaultArtifact == ear
        1 * publications.remove(exe)
        1 * publications.add(ear)
    }

    def "current default is cached after realizing the provider"() {
        def jar = artifact("jar")
        def war = artifact("war")

        when:
        publication.addCandidate(jar)

        then:
        publication.defaultArtifactProvider.get() == jar

        when:
        publication.addCandidate(war)

        then:
        publication.defaultArtifactProvider.get() == war

        then:
        1 * publications.remove(jar)
        1 * publications.add(war)

        then:
        publication.defaultArtifactProvider.get() == war

        and:
        0 * publications._
    }

    def PublishArtifact artifact(String type) {
        PublishArtifact artifact = Stub() {
            toString() >> type
            getType() >> type
        }
        return artifact
    }
}
