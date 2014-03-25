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
package org.gradle.integtests.resolve.maven
import org.gradle.api.artifacts.resolution.JvmLibraryJavadocArtifact
import org.gradle.api.artifacts.resolution.JvmLibrarySourcesArtifact
import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.resolve.JvmLibraryArtifactResolveTestFixture

class MavenJvmLibraryArtifactResolutionIntegrationTest extends AbstractDependencyResolutionTest {
    def repo = mavenHttpRepo
    def module = repo.module("some.group", "some-artifact", "1.0")
    JvmLibraryArtifactResolveTestFixture fixture

    def setup() {
        server.start()
        fixture = new JvmLibraryArtifactResolveTestFixture(buildFile)
        fixture.withRepository("maven { url '$repo.uri' }")
    }

    def "resolves and caches sources artifacts"() {
        publishArtifacts("sources", "javadoc")

        fixture.requestingTypes(JvmLibrarySourcesArtifact)
                .expectSourceArtifact("some-artifact-1.0-sources.jar")
                .prepare()

        when:
        module.pom.expectGet()
        module.getArtifact(classifier: "sources").expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "resolve javadoc artifacts"() {
        publishArtifacts("sources", "javadoc")

        fixture.requestingTypes(JvmLibraryJavadocArtifact)
                .expectJavadocArtifact("some-artifact-1.0-javadoc.jar")
                .prepare()

        when:
        module.pom.expectGet()
        module.artifact(classifier: "javadoc").expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "resolves and caches all artifacts"() {
        publishArtifacts("sources", "javadoc")

        fixture.requestingTypes()
                .expectSourceArtifact("some-artifact-1.0-sources.jar")
                .expectJavadocArtifact("some-artifact-1.0-javadoc.jar")
                .prepare()

        when:
        module.pom.expectGet()
        module.artifact(classifier: "sources").expectGet()
        module.artifact(classifier: "javadoc").expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "resolves artifacts of non-existing component"() {
        fixture.expectComponentNotFound().prepare()

        when:
        module.pom.expectGetMissing()
        module.artifact.expectHeadMissing()

        then:
        succeeds("verify")
    }

    def "resolve and caches missing artifacts of existing component"() {
        publishArtifacts("sources", "javadoc")
        // TODO:DAZ These artifacts should be missing, not failures
        fixture.requestingTypes()
                .expectSourceArtifactFailure("Artifact 'some.group:some-artifact:1.0:some-artifact-sources.jar' not found.")
                .expectJavadocArtifactFailure("Artifact 'some.group:some-artifact:1.0:some-artifact-javadoc.jar' not found.")
                .prepare()

        when:
        module.pom.expectGet()
        module.artifact(classifier: "sources").expectGetMissing()
        module.artifact(classifier: "javadoc").expectGetMissing()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "resolves and caches partially missing artifacts"() {
        publishArtifacts("sources")

        fixture.requestingTypes()
                .expectSourceArtifact("some-artifact-1.0-sources.jar")
                .expectJavadocArtifactFailure("Artifact 'some.group:some-artifact:1.0:some-artifact-javadoc.jar' not found.")
                .prepare()

        when:
        module.pom.expectGet()
        module.artifact(classifier: "sources").expectGet()
        module.artifact(classifier: "javadoc").expectGetMissing()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "resolve and does not cache broken artifacts"() {
        publishArtifacts("sources")

        fixture.requestingTypes()
                .expectSourceArtifact("some-artifact-1.0-sources.jar")
                .expectJavadocArtifactFailure("Could not download artifact 'some.group:some-artifact:1.0:some-artifact-javadoc.jar'")
                .prepare()

        when:
        module.pom.expectGet()
        module.artifact(classifier: "sources").expectGet()
        module.artifact(classifier: "javadoc").expectGetBroken()


        then:
        succeeds("verify")

        when:
        server.resetExpectations()
        // Only the broken artifact is not cached
        module.artifact(classifier: "javadoc").expectGetBroken()

        then:
        succeeds("verify")
    }

    def checkArtifactsResolvedAndCached() {
        assert succeeds("verify")
        server.resetExpectations()
        assert succeeds("verify")
        true
    }

    private publishArtifacts(String... classifiers) {
        for (classifier in classifiers) {
            module.artifact(classifier: classifier)
        }
        module.publish()
    }
}