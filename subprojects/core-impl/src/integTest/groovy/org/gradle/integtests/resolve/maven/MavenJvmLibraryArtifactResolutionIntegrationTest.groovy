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

    // TODO:DAZ Test for regular cache expiry
    def "fetches missing snapshot artifacts with --refresh-dependencies"() {
        def snapshotModule = repo.module("some.group", "some-artifact", "1.0-SNAPSHOT")
        def snapshotSources = snapshotModule.artifact(classifier: "sources")
        snapshotModule.publish()

        fixture.withComponentVersion("1.0-SNAPSHOT")
                .requestingTypes(JvmLibrarySourcesArtifact)
                .expectSourceArtifactNotFound("some-artifact-sources.jar")
                .prepare()

        when:
        snapshotModule.metaData.expectGet()
        snapshotModule.pom.expectGet()
        snapshotSources.expectGetMissing()

        then:
        checkArtifactsResolvedAndCached()

        when:
        executer.withArgument("--refresh-dependencies")
        fixture.clearExpectations()
                .expectSourceArtifact("some-artifact-1.0-SNAPSHOT-sources.jar")
                .createVerifyTask("verifyRefresh")

        and:
        snapshotModule.metaData.expectGet()
        snapshotModule.pom.expectHead()
        snapshotSources.expectGet()

        then:
        succeeds("verifyRefresh")
    }

    def "updates snapshot artifacts with --refresh-dependencies"() {
        def snapshotModule = repo.module("some.group", "some-artifact", "1.0-SNAPSHOT")
        def snapshotSources = snapshotModule.artifact(classifier: "sources")
        snapshotModule.publish()

        fixture.withComponentVersion("1.0-SNAPSHOT")
                .requestingTypes(JvmLibrarySourcesArtifact)
                .expectSourceArtifact("some-artifact-1.0-SNAPSHOT-sources.jar")
                .prepare()

        when:
        snapshotModule.metaData.expectGet()
        snapshotModule.pom.expectGet()
        snapshotSources.expectGet()

        then:
        checkArtifactsResolvedAndCached()

        when:
        def snapshot = file("sources/some-artifact-1.0-SNAPSHOT-sources.jar").snapshot()
        snapshotModule.publishWithChangedContent()
        executer.withArgument("--refresh-dependencies")

        and:
        snapshotModule.metaData.expectGet()
        snapshotModule.pom.expectHead()
        snapshotModule.pom.sha1.expectGet()
        snapshotModule.pom.expectGet()
        snapshotSources.expectHead()
        snapshotSources.sha1.expectGet()
        snapshotSources.expectGet()

        then:
        succeeds("verify")
        file("sources/some-artifact-1.0-SNAPSHOT-sources.jar").assertHasChangedSince(snapshot)
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
                .expectSourceArtifactNotFound("some-artifact-sources.jar")
                .expectJavadocArtifactNotFound("some-artifact-javadoc.jar")
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
                .expectJavadocArtifactNotFound("some-artifact-javadoc.jar")
                .prepare()

        when:
        module.pom.expectGet()
        module.artifact(classifier: "sources").expectGet()
        module.artifact(classifier: "javadoc").expectGetMissing()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "resolves and recovers from broken artifacts"() {
        publishArtifacts("sources", "javadoc")

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
        fixture.clearExpectations()
                .expectSourceArtifact("some-artifact-1.0-sources.jar")
                .expectJavadocArtifact("some-artifact-1.0-javadoc.jar")
                .createVerifyTask("verifyFixed")

        and:
        server.resetExpectations()
        // Only the broken artifact is not cached
        module.artifact(classifier: "javadoc").expectGet()

        then:
        succeeds("verifyFixed")
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