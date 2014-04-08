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
package org.gradle.integtests.resolve.ivy

import org.gradle.api.artifacts.result.jvm.JvmLibraryJavadocArtifact
import org.gradle.api.artifacts.result.jvm.JvmLibrarySourcesArtifact
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ArtifactResolveException
import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.resolve.JvmLibraryArtifactResolveTestFixture
import org.gradle.test.fixtures.ivy.IvyRepository
import spock.lang.Unroll

// TODO:DAZ Test can resolve multiple source/javadoc artifacts declared in 'sources'/'javadoc' configuration
class IvyJvmLibraryArtifactResolutionIntegrationTest extends AbstractDependencyResolutionTest {
    def fileRepo = ivyRepo
    def httpRepo = ivyHttpRepo
    def module = httpRepo.module("some.group", "some-artifact", "1.0")
    JvmLibraryArtifactResolveTestFixture fixture

    def setup() {
        server.start()
        initBuild(httpRepo)

        fixture = new JvmLibraryArtifactResolveTestFixture(buildFile)

        publishModule()
    }

    def initBuild(IvyRepository repo) {
        buildFile.text = """
repositories {
    ivy { url '$repo.uri' }
}
"""
    }

    def "resolve sources artifacts"() {
        fixture.requestingTypes(JvmLibrarySourcesArtifact)
                .expectSourceArtifact("my-sources")
                .prepare()

        when:
        module.ivy.expectGet()
        module.getArtifact(classifier: "my-sources").expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "resolve javadoc artifacts"() {
        fixture.requestingTypes(JvmLibraryJavadocArtifact)
                .expectJavadocArtifact("my-javadoc")
                .prepare()

        when:
        module.ivy.expectGet()
        module.getArtifact(classifier: "my-javadoc").expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "resolve all artifacts"() {
        fixture.expectSourceArtifact("my-sources")
                .expectJavadocArtifact("my-javadoc")
                .prepare()

        when:
        module.ivy.expectGet()
        module.getArtifact(classifier: "my-sources").expectGet()
        module.getArtifact(classifier: "my-javadoc").expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    @Unroll
    def "fetches missing artifacts for module #condition"() {
        fixture.requestingTypes(JvmLibrarySourcesArtifact)
                .expectSourceArtifactNotFound("my-sources")
                .prepare()
        buildFile << """
dependencies {
    components {
        eachComponent { ComponentMetadataDetails details ->
            details.changing = true
        }
    }
}

if (project.hasProperty('nocache')) {
    configurations.all {
        resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
    }
}
"""

        when:
        module.ivy.expectGet()
        module.getArtifact(classifier: "my-sources").expectGetMissing()

        then:
        checkArtifactsResolvedAndCached()

        when:
        module.publishWithChangedContent()
        fixture.clearExpectations()
                .expectSourceArtifact("my-sources")
                .createVerifyTask("verifyRefresh")

        and:
        server.resetExpectations()
        module.ivy.expectHead()
        module.ivy.sha1.expectGet()
        module.ivy.expectGet()
        module.getArtifact(classifier: "my-sources").expectGet()

        then:
        executer.withArgument(execArg)
        succeeds("verifyRefresh")

        where:
        condition                     | execArg
        "with --refresh-dependencies" | "--refresh-dependencies"
        "when ivy descriptor changes" | "-Pnocache"
    }

    @Unroll
    def "updates artifacts for module #condition"() {
        buildFile << """
dependencies {
    components {
        eachComponent { ComponentMetadataDetails details ->
            details.changing = true
        }
    }
}

if (project.hasProperty('nocache')) {
    configurations.all {
        resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
    }
}
"""

        final sourceArtifact = module.getArtifact(classifier: "my-sources")
        fixture.requestingTypes(JvmLibrarySourcesArtifact)
                .expectSourceArtifact("my-sources")
                .prepare()

        when:
        module.ivy.expectGet()
        sourceArtifact.expectGet()

        then:
        checkArtifactsResolvedAndCached()

        when:
        def snapshot = file("sources/some-artifact-1.0-my-sources.jar").snapshot()
        module.publishWithChangedContent()

        and:
        server.resetExpectations()
        module.ivy.expectHead()
        module.ivy.sha1.expectGet()
        module.ivy.expectGet()
        sourceArtifact.expectHead()
        sourceArtifact.sha1.expectGet()
        sourceArtifact.expectGet()

        then:
        executer.withArgument(execArg)
        succeeds("verify")
        file("sources/some-artifact-1.0-my-sources.jar").assertHasChangedSince(snapshot)

        where:
        condition                     | execArg
        "with --refresh-dependencies" | "--refresh-dependencies"
        "when ivy descriptor changes" | "-Pnocache"
    }

    def "reports failure to resolve artifacts of non-existing component"() {
        fixture.expectComponentNotFound().prepare()

        when:
        module.ivy.expectGetMissing()
        module.jar.expectHeadMissing()

        then:
        succeeds("verify")
    }

    def "reports failure to resolve missing artifacts"() {
        fixture.expectSourceArtifactNotFound("my-sources")
                .expectJavadocArtifactNotFound("my-javadoc")
                .prepare()

        when:
        module.ivy.expectGet()
        module.getArtifact(classifier: "my-sources").expectGetMissing()
        module.getArtifact(classifier: "my-javadoc").expectGetMissing()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "resolves when some artifacts are missing"() {
        fixture.expectSourceArtifact("my-sources")
                .expectJavadocArtifactNotFound("my-javadoc")
                .prepare()

        when:
        module.ivy.expectGet()
        module.getArtifact(classifier: "my-sources").expectGet()
        module.getArtifact(classifier: "my-javadoc").expectGetMissing()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "resolves when some artifacts are broken"() {
        fixture.expectSourceArtifact("my-sources")
                .expectJavadocArtifactFailure(new ArtifactResolveException("Could not download artifact 'some.group:some-artifact:1.0:some-artifact-my-javadoc.jar'"))
                .prepare()

        when:
        module.ivy.expectGet()
        module.getArtifact(classifier: "my-sources").expectGet()
        module.getArtifact(classifier: "my-javadoc").expectGetBroken()

        then:
        succeeds("verify")

        when:
        fixture.clearExpectations()
                .expectSourceArtifact("my-sources")
                .expectJavadocArtifact("my-javadoc")
                .createVerifyTask("verifyFixed")

        and:
        server.resetExpectations()
        // Only the broken artifact is not cached
        module.getArtifact(classifier: "my-javadoc").expectGet()

        then:
        succeeds("verifyFixed")
    }

    def "resolve and does not cache artifacts from local repository"() {
        initBuild(fileRepo)

        fixture.expectSourceArtifact("my-sources")
                .expectJavadocArtifact("my-javadoc")
                .prepare()

        when:
        succeeds("verify")

        and:
        def snapshot = file("sources/some-artifact-1.0-my-sources.jar").snapshot()

        and:
        module.publishWithChangedContent()

        then:
        succeeds("verify")
        file("sources/some-artifact-1.0-my-sources.jar").assertHasChangedSince(snapshot)
    }

    def "can resolve artifacts with maven scheme from ivy repository"() {
        // Published with no configurations, and a source artifact only
        def moduleWithMavenScheme = httpRepo.module("some.group", "some-artifact", "1.1")
        moduleWithMavenScheme.artifact(classifier: "sources")
        moduleWithMavenScheme.publish()

        fixture.withComponentVersion("some.group", "some-artifact", "1.1")
                .requestingTypes(JvmLibrarySourcesArtifact, JvmLibraryJavadocArtifact)
                .expectSourceArtifact("sources")
                .prepare()

        when:
        moduleWithMavenScheme.ivy.expectGet()
        moduleWithMavenScheme.getArtifact(classifier: "sources").expectHead()
        moduleWithMavenScheme.getArtifact(classifier: "sources").expectGet()
        moduleWithMavenScheme.getArtifact(classifier: "javadoc").expectHeadMissing()

        then:
        checkArtifactsResolvedAndCached()
    }

    def checkArtifactsResolvedAndCached() {
        assert succeeds("verify")
        server.resetExpectations()
        assert succeeds("verify")
        true
    }

    private publishModule() {
        module.configuration("sources")
        module.configuration("javadoc")
        // use uncommon classifiers that are different from those used by maven, 
        // in order to prove that artifact names don't matter
        module.artifact(type: "source", classifier: "my-sources", ext: "jar", conf: "sources")
        module.artifact(type: "javadoc", classifier: "my-javadoc", ext: "jar", conf: "javadoc")
        module.publish()
    }
}