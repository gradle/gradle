/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.resolve.result

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.internal.resolve.ArtifactNotFoundException
import org.gradle.internal.resolve.ArtifactResolveException
import spock.lang.Specification

class DefaultBuildableArtifactFileResolveResultTest extends Specification {

    final result = new DefaultBuildableArtifactFileResolveResult()
    final artifactFile = new File("artifact.jar")
    final artifactId = Stub(ComponentArtifactIdentifier) {
        getDisplayName() >> "<artifact>"
    }

    def "has no result by default"() {
        expect:
        !result.hasResult()
        !result.isSuccessful()
        !result.isNotFound()
    }

    def "can have file result"() {
        when:
        result.resolved(artifactFile)

        then:
        result.result == artifactFile
        result.failure == null
        result.hasResult()
        result.isSuccessful()
        !result.isNotFound()
    }

    def "can have not found result"() {
        when:
        result.attempted("http://somewhere/artifact.jar")
        result.notFound(artifactId)

        then:
        result.hasResult()
        result.isNotFound()
        !result.isSuccessful()
        result.failure == null

        and:
        def failure = result.notFoundFailure
        failure instanceof ArtifactNotFoundException
        failure.message.contains("http://somewhere/artifact.jar")

        when:
        result.result

        then:
        thrown(ArtifactNotFoundException)
    }

    def "cannot get not found failure when artifact was found"() {
        when:
        result.resolved(artifactFile)
        result.notFoundFailure

        then:
        thrown(IllegalStateException)
    }

    def "can have failure result"() {
        def failure = new ArtifactResolveException("broken")

        when:
        result.failed(failure)

        then:
        result.failure == failure
        result.hasResult()
        !result.isSuccessful()
        !result.isNotFound()

        when:
        result.result

        then:
        ArtifactResolveException e = thrown()
        e == failure
    }

    def "cannot get file when no result specified"() {
        when:
        result.result

        then:
        thrown(IllegalStateException)

        when:
        result.failure

        then:
        thrown(IllegalStateException)
    }

}
