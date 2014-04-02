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

import org.gradle.api.artifacts.result.jvm.JvmLibraryJavadocArtifact
import org.gradle.api.artifacts.result.jvm.JvmLibrarySourcesArtifact
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ArtifactResolveException
import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.resolve.JvmLibraryArtifactResolveTestFixture
import org.gradle.test.fixtures.maven.MavenRepository
import spock.lang.Unroll

class MavenJvmLibraryArtifactResolutionIntegrationTest extends AbstractDependencyResolutionTest {
    def repo = mavenHttpRepo
    def fileRepo = mavenRepo
    def module = repo.module("some.group", "some-artifact", "1.0")
    def sourceArtifact = module.artifact(classifier: "sources")
    def javadocArtifact = module.artifact(classifier: "javadoc")
    JvmLibraryArtifactResolveTestFixture fixture

    def setup() {
        server.start()
        initBuild(repo)

        fixture = new JvmLibraryArtifactResolveTestFixture(buildFile)

        module.publish()
    }

    def initBuild(MavenRepository repo, String module = "some.group:some-artifact:1.0") {
        buildFile.text = """
repositories {
    maven { url '$repo.uri' }
}
configurations { compile }
dependencies {
    compile "${module}"
}
"""
    }

    def "resolves and caches source artifacts"() {
        fixture.requestingTypes(JvmLibrarySourcesArtifact)
                .expectSourceArtifact("sources")
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
                .expectJavadocArtifact("javadoc")
                .prepare()

        when:
        module.pom.expectGet()
        javadocArtifact.expectHead()
        javadocArtifact.expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "resolves and caches all artifacts"() {
        fixture.expectSourceArtifact("sources")
                .expectJavadocArtifact("javadoc")
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

    @Unroll
    def "fetches missing snapshot artifacts #condition"() {
        initBuild(repo, "some.group:some-artifact:1.0-SNAPSHOT")
        buildFile << """
if (project.hasProperty('nocache')) {
    configurations.compile.resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
}
"""

        def snapshotModule = repo.module("some.group", "some-artifact", "1.0-SNAPSHOT")
        def snapshotSources = snapshotModule.artifact(classifier: "sources")
        snapshotModule.publish()

        fixture.withComponentVersion("some.group", "some-artifact", "1.0-SNAPSHOT")
                .requestingTypes(JvmLibrarySourcesArtifact)
                .prepare()

        when:
        snapshotModule.metaData.expectGet()
        snapshotModule.pom.expectGet()
        snapshotSources.expectHeadMissing()

        then:
        checkArtifactsResolvedAndCached()

        when:
        snapshotModule.publishWithChangedContent()
        fixture.clearExpectations()
                .expectSourceArtifact("sources")
                .createVerifyTask("verifyRefresh")

        and:
        server.resetExpectations()
        snapshotModule.metaData.expectGet()
        snapshotModule.pom.expectHead()
        snapshotModule.pom.sha1.expectGet()
        snapshotModule.pom.expectGet()
        snapshotSources.expectHead()
        snapshotSources.expectGet()

        then:
        executer.withArgument(execArg)
        succeeds("verifyRefresh")

        where:
        condition | execArg
        "with --refresh-dependencies" | "--refresh-dependencies"
        "when snapshot pom changes" | "-Pnocache"
    }

    @Unroll
    def "updates snapshot artifacts #condition"() {
        initBuild(repo, "some.group:some-artifact:1.0-SNAPSHOT")
        buildFile << """
if (project.hasProperty('nocache')) {
    configurations.compile.resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
}
"""

        def snapshotModule = repo.module("some.group", "some-artifact", "1.0-SNAPSHOT")
        def snapshotSources = snapshotModule.artifact(classifier: "sources")
        snapshotModule.publish()

        fixture.withComponentVersion("some.group", "some-artifact", "1.0-SNAPSHOT")
                .requestingTypes(JvmLibrarySourcesArtifact)
                .expectSourceArtifact("sources")
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

        and:
        server.resetExpectations()
        snapshotModule.metaData.expectGet()
        snapshotModule.pom.expectHead()
        snapshotModule.pom.sha1.expectGet()
        snapshotModule.pom.expectGet()
        snapshotSources.expectHead()
        // TODO:DAZ Extra head request should not be required
        snapshotSources.expectHead()
        snapshotSources.sha1.expectGet()
        snapshotSources.expectGet()

        then:
        executer.withArgument(execArg)
        succeeds("verify")
        file("sources/some-artifact-1.0-SNAPSHOT-sources.jar").assertHasChangedSince(snapshot)

        where:
        condition | execArg
        "with --refresh-dependencies" | "--refresh-dependencies"
        "when snapshot pom changes" | "-Pnocache"
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
        fixture.prepare()

        when:
        module.pom.expectGet()
        sourceArtifact.expectHeadMissing()
        javadocArtifact.expectHeadMissing()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "resolves and caches artifacts where some are present"() {
        fixture.expectSourceArtifact("sources")
                .prepare()

        when:
        module.pom.expectGet()
        sourceArtifact.expectHead()
        sourceArtifact.expectGet()
        javadocArtifact.expectHeadMissing()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "reports on failure to list artifacts and recovers on subsequent resolve"() {
        fixture.requestingTypes(JvmLibrarySourcesArtifact)
                .expectComponentResolutionFailure(new ArtifactResolveException("Could not determine artifacts for component 'some.group:some-artifact:1.0'"))
                .prepare()

        when:
        module.pom.expectGet()
        sourceArtifact.expectHeadBroken()

        then:
        succeeds("verify")

        when:
        fixture.clearExpectations()
                .expectSourceArtifact("sources")
                .createVerifyTask("verifyFixed")

        and:
        server.resetExpectations()
        sourceArtifact.expectHead()
        sourceArtifact.expectGet()

        then:
        succeeds("verifyFixed")
    }

    def "resolves and recovers from broken artifacts"() {
        fixture.requestingTypes(JvmLibraryJavadocArtifact)
                .expectJavadocArtifactFailure(new ArtifactResolveException("Could not download artifact 'some.group:some-artifact:1.0:some-artifact-javadoc.jar'"))
                .prepare()

        when:
        module.pom.expectGet()
        javadocArtifact.expectHead()
        javadocArtifact.expectGetBroken()

        then:
        succeeds("verify")

        when:
        fixture.clearExpectations()
                .expectJavadocArtifact("javadoc")
                .createVerifyTask("verifyFixed")

        and:
        server.resetExpectations()
        javadocArtifact.expectGet()

        then:
        succeeds("verifyFixed")
    }

    def "resolve and does not cache artifacts from local repository"() {
        initBuild(fileRepo)

        fixture.requestingTypes(JvmLibrarySourcesArtifact)
                .expectSourceArtifact("sources")
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