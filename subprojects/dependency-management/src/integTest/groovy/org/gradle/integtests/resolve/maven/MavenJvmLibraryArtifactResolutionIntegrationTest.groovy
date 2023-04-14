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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.resolve.JvmLibraryArtifactResolveTestFixture
import org.gradle.test.fixtures.maven.MavenRepository

class MavenJvmLibraryArtifactResolutionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def repo = mavenHttpRepo
    def fileRepo = mavenRepo
    def module = repo.module("some.group", "some-artifact", "1.0")
    def sourceArtifact = module.artifact(classifier: "sources")
    def javadocArtifact = module.artifact(classifier: "javadoc")
    JvmLibraryArtifactResolveTestFixture fixture

    def setup() {
        initBuild(repo)

        fixture = new JvmLibraryArtifactResolveTestFixture(buildFile)

        module.publish()
    }

    def initBuild(MavenRepository repo) {
        buildFile.text = """
repositories {
    maven { url '$repo.uri' }
}
"""
    }

    def "resolves and caches source artifacts"() {
        fixture.requestingSource()
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
        fixture.requestingJavadoc()
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

    def "fetches missing snapshot artifacts #condition"() {
        buildFile << """
if (project.hasProperty('nocache')) {
    configurations.all {
        resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
    }
}
"""

        def snapshotModule = repo.module("some.group", "some-artifact", "1.0-SNAPSHOT")
        def snapshotSources = snapshotModule.artifact(classifier: "sources")
        snapshotModule.publish()

        fixture.withComponentVersion("some.group", "some-artifact", "1.0-SNAPSHOT")
                .withSnapshotTimestamp(snapshotModule.uniqueSnapshotVersion)
                .requestingSource()
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
                .withSnapshotTimestamp(snapshotModule.uniqueSnapshotVersion)
                .expectSourceArtifact("sources")
                .createVerifyTask("verifyRefresh")

        and:
        server.resetExpectations()
        snapshotModule.metaData.expectHead()
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

    def "updates snapshot artifacts #condition"() {
        buildFile << """
if (project.hasProperty('nocache')) {
    configurations.all {
        resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
    }
}
"""

        def snapshotModule = repo.module("some.group", "some-artifact", "1.0-SNAPSHOT")
        def snapshotSources = snapshotModule.artifact(classifier: "sources")
        snapshotModule.publish()

        fixture.withComponentVersion("some.group", "some-artifact", "1.0-SNAPSHOT")
                .withSnapshotTimestamp(snapshotModule.uniqueSnapshotVersion)
                .requestingSource()
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
        fixture.withSnapshotTimestamp(snapshotModule.uniqueSnapshotVersion)
                .createVerifyTask("verifyRefresh")

        and:
        server.resetExpectations()
        snapshotModule.metaData.expectHead()
        snapshotModule.metaData.expectGet()
        snapshotModule.pom.expectHead()
        snapshotModule.pom.sha1.expectGet()
        snapshotModule.pom.expectGet()
        snapshotSources.expectHead()
        // TODO Extra head request should not be required
        snapshotSources.expectHead()
        snapshotSources.sha1.expectGet()
        snapshotSources.expectGet()

        then:
        executer.withArgument(execArg)
        succeeds("verifyRefresh")
        file("sources/some-artifact-1.0-SNAPSHOT-sources.jar").assertHasChangedSince(snapshot)

        where:
        condition | execArg
        "with --refresh-dependencies" | "--refresh-dependencies"
        "when snapshot pom changes"   | "-Pnocache"
    }

    @ToBeFixedForConfigurationCache(because = "does not check for missing artifact on second invocation")
    def "reports failure to resolve artifacts of non-existing component"() {
        fixture.expectComponentNotFound().prepare()

        when:
        module.pom.expectGetMissing()

        then:
        fails("verify")
        failure.assertHasCause("""Could not find some.group:some-artifact:1.0.
Searched in the following locations:
  - ${module.pom.uri}""")

        when:
        server.resetExpectations()
        module.pom.expectGetMissing()

        then:
        fails("verify")
        failure.assertHasCause("""Could not find some.group:some-artifact:1.0.
Searched in the following locations:
  - ${module.pom.uri}""")
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
        fixture.requestingSource()
                .expectComponentResolutionFailure()
                .prepare()

        when:
        module.pom.expectGet()
        sourceArtifact.expectHeadBroken()

        then:
        fails("verify")
        failure.assertHasCause("Could not determine artifacts for some.group:some-artifact:1.0")
        failure.assertHasCause("Could not HEAD '${sourceArtifact.uri}'. Received status code 500 from server: broken")

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
        fixture.requestingJavadoc()
                .expectJavadocArtifactFailure()
                .prepare()

        when:
        module.pom.expectGet()
        javadocArtifact.expectHead()
        javadocArtifact.expectGetBroken()

        then:
        fails("verify")
        failure.assertHasCause("Could not download some-artifact-1.0-javadoc.jar (some.group:some-artifact:1.0)")
        failure.assertHasCause("Could not get resource '${javadocArtifact.uri}'")
        failure.assertHasCause("Could not GET '${javadocArtifact.uri}'. Received status code 500 from server: broken")

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

        fixture.requestingSource()
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
