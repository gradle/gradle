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
    def fileRepo = mavenRepo
    def module = repo.module("some.group", "some-artifact", "1.0")
    def sourceArtifact = module.artifact(classifier: "sources")
    def javadocArtifact = module.artifact(classifier: "javadoc")
    JvmLibraryArtifactResolveTestFixture fixture

    def setup() {
        server.start()
        fixture = new JvmLibraryArtifactResolveTestFixture(buildFile)
        fixture.withRepository("maven { url '$repo.uri' }")

        module.publish()
    }

    def "resolves and caches source artifacts"() {
        fixture.requestingTypes(JvmLibrarySourcesArtifact)
                .expectSourceArtifact("some-artifact-1.0-sources.jar")
                .prepare()

        when:
        module.pom.expectGet()
        sourceArtifact.expectHead()
        sourceArtifact.expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "resolve javadoc artifacts"() {
        fixture.requestingTypes(JvmLibraryJavadocArtifact)
                .expectJavadocArtifact("some-artifact-1.0-javadoc.jar")
                .prepare()

        when:
        module.pom.expectGet()
        javadocArtifact.expectHead()
        javadocArtifact.expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "resolves and caches all artifacts"() {
        fixture.requestingTypes()
                .expectSourceArtifact("some-artifact-1.0-sources.jar")
                .expectJavadocArtifact("some-artifact-1.0-javadoc.jar")
                .prepare()

        when:
        module.pom.expectGet()
        sourceArtifact.expectHead()
        sourceArtifact.expectGet()
        javadocArtifact.expectHead()
        javadocArtifact.expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    // TODO:DAZ Test for regular cache expiry, once it can be configured
    def "fetches missing snapshot artifacts with --refresh-dependencies"() {
        def snapshotModule = repo.module("some.group", "some-artifact", "1.0-SNAPSHOT")
        def snapshotSources = snapshotModule.artifact(classifier: "sources")
        snapshotModule.publish()

        fixture.withComponentVersion("1.0-SNAPSHOT")
                .requestingTypes(JvmLibrarySourcesArtifact)
                .prepare()

        when:
        snapshotModule.metaData.expectGet()
        snapshotModule.pom.expectGet()
        snapshotSources.expectHeadMissing()

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
        snapshotSources.expectHead()
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
        snapshotSources.expectHead()
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
        // TODO:DAZ This extra head request should not be required
        snapshotSources.expectHead()
        snapshotSources.sha1.expectGet()
        snapshotSources.expectGet()

        then:
        succeeds("verify")
        file("sources/some-artifact-1.0-SNAPSHOT-sources.jar").assertHasChangedSince(snapshot)
    }

    def "reports failure to resolve artifacts of non-existing component"() {
        fixture.expectComponentNotFound().prepare()

        when:
        module.pom.expectGetMissing()
        module.artifact.expectHeadMissing()

        then:
        succeeds("verify")
    }

    def "resolve and caches missing artifacts of existing component"() {
        fixture.requestingTypes().prepare()

        when:
        module.pom.expectGet()
        sourceArtifact.expectHeadMissing()
        javadocArtifact.expectHeadMissing()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "resolves and caches artifacts where some are present"() {
        fixture.requestingTypes()
                .expectSourceArtifact("some-artifact-1.0-sources.jar")
                .prepare()

        when:
        module.pom.expectGet()
        sourceArtifact.expectHead()
        sourceArtifact.expectGet()
        javadocArtifact.expectHeadMissing()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "resolves and recovers from broken artifacts"() {
        fixture.requestingTypes()
                .expectSourceArtifactListFailure("Could not determine artifacts for component 'some.group:some-artifact:1.0'")
                .expectJavadocArtifactFailure("Could not download artifact 'some.group:some-artifact:1.0:some-artifact-javadoc.jar'")
                .prepare()

        when:
        module.pom.expectGet()
        sourceArtifact.expectHeadBroken()
        javadocArtifact.expectHead()
        javadocArtifact.expectGetBroken()

        then:
        succeeds("verify")

        when:
        fixture.clearExpectations()
                .expectSourceArtifact("some-artifact-1.0-sources.jar")
                .expectJavadocArtifact("some-artifact-1.0-javadoc.jar")
                .createVerifyTask("verifyFixed")

        and:
        server.resetExpectations()
        sourceArtifact.expectHead()
        sourceArtifact.expectGet()
        javadocArtifact.expectGet()

        then:
        succeeds("verifyFixed")
    }

    def "resolve and does not cache artifacts from local repository"() {
        fixture.withRepository("maven { url '$fileRepo.uri' }")
                .requestingTypes(JvmLibrarySourcesArtifact)
                .expectSourceArtifact("some-artifact-1.0-sources.jar")
                .prepare()

        when:
        succeeds("verify")

        and:
        def snapshot = file("sources/some-artifact-1.0-sources.jar").snapshot()

        and:
        module.publishWithChangedContent()

        then:
        succeeds("verify")
        file("sources/some-artifact-1.0-sources.jar").assertHasChangedSince(snapshot)
    }

    def checkArtifactsResolvedAndCached() {
        assert succeeds("verify")
        server.resetExpectations()
        assert succeeds("verify")
        true
    }
}