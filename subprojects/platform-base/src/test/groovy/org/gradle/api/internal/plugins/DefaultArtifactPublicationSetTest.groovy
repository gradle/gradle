/*
 * Copyright 2023 the original author or authors.
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

class DefaultArtifactPublicationSetTest extends Specification {
    final PublishArtifactSet publications = Mock()
    final DefaultArtifactPublicationSet publication = TestUtil.newInstance(DefaultArtifactPublicationSet, publications)

    def "adds artifacts to wrapped artifact set"() {
        given:
        def artifact = artifact("foo")

        when:
        publication.addCandidate(artifact)

        then:
        1 * publications.add(artifact)
    }

    PublishArtifact artifact(String type) {
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
