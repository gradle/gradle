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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.resolve.JvmLibraryArtifactResolveTestFixture
import org.gradle.test.fixtures.ivy.IvyRepository

class IvyJvmLibraryArtifactResolutionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def fileRepo = ivyRepo
    def httpRepo = ivyHttpRepo
    def module = httpRepo.module("some.group", "some-artifact", "1.0")
    JvmLibraryArtifactResolveTestFixture fixture

    def setup() {
        initBuild(httpRepo)

        fixture = new JvmLibraryArtifactResolveTestFixture(buildFile)

        publishModule()
    }

    def initBuild(IvyRepository repo) {
        buildFile.text = """
repositories {
    ivy { url = '$repo.uri' }
}
"""
    }

    def "resolve sources artifacts"() {
        fixture.requestingSource()
                .expectSourceArtifact("my-sources")
                .prepare()

        when:
        module.ivy.expectGet()
        module.getArtifact(classifier: "my-sources").expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "resolve javadoc artifacts"() {
        fixture.requestingJavadoc()
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

    def "resolves multiple artifacts of the same type"() {
        given:
        module.artifact(type: "source", classifier: "other-sources", ext: "jar", conf: "sources")
        module.artifact(type: "javadoc", classifier: "other-javadoc", ext: "jar", conf: "javadoc")
        module.publish()

        fixture.expectSourceArtifact("my-sources")
                .expectSourceArtifact("other-sources")
                .expectJavadocArtifact("my-javadoc")
                .expectJavadocArtifact("other-javadoc")
                .prepare()

        when:
        module.ivy.expectGet()
        module.getArtifact(classifier: "my-sources").expectGet()
        module.getArtifact(classifier: "other-sources").expectGet()
        module.getArtifact(classifier: "my-javadoc").expectGet()
        module.getArtifact(classifier: "other-javadoc").expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "resolves when configurations are present and empty"() {
        given:
        def module1 = httpRepo.module("some.group", "some-artifact", "1.1")
        module1.configuration("sources")
        module1.configuration("javadoc")
        // Add an artifact to prevent the default artifact being added with conf='*'
        module1.artifact([conf: 'default'])
        module1.publish()

        and:
        fixture.withComponentVersion("some.group", "some-artifact", "1.1").prepare()

        when:
        module1.ivy.expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "fetches missing artifacts for module #condition"() {
        fixture.requestingSource()
                .expectSourceArtifactNotFound("my-sources")
                .prepare()
        buildFile << """
class ChangingRule implements ComponentMetadataRule {
    @Override
    void execute(ComponentMetadataContext context) {
        context.details.changing = true
    }
}

dependencies {
    components {
        all(ChangingRule)
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
        def sourceArtifact = module.getArtifact(classifier: "my-sources")
        sourceArtifact.expectGetMissing()

        then:
        fails("verify")
        failure.assertHasCause("""Could not find some-artifact-1.0-my-sources.jar (some.group:some-artifact:1.0).
Searched in the following locations:
    ${sourceArtifact.uri}""")

        when:
        server.resetExpectations()

        then:
        fails("verify")
        failure.assertHasCause("""Could not find some-artifact-1.0-my-sources.jar (some.group:some-artifact:1.0).
Searched in the following locations:
    ${sourceArtifact.uri}""")

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
        sourceArtifact.expectGet()

        then:
        executer.withArgument(execArg)
        succeeds("verifyRefresh")

        where:
        condition                     | execArg
//        "with --refresh-dependencies" | "--refresh-dependencies"
        "when ivy descriptor changes" | "-Pnocache"
    }

    def "updates artifacts for module #condition"() {
        buildFile << """
class ChangingRule implements ComponentMetadataRule {
    @Override
    void execute(ComponentMetadataContext context) {
        context.details.changing = true
    }
}

dependencies {
    components {
        all(ChangingRule)
    }
}

if (project.hasProperty('nocache')) {
    configurations.all {
        resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
    }
}
"""

        final sourceArtifact = module.getArtifact(classifier: "my-sources")
        fixture.requestingSource()
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

    @ToBeFixedForConfigurationCache(because = "does not check for missing artifact on second invocation")
    def "reports failure to resolve artifacts of non-existing component"() {
        fixture.expectComponentNotFound().prepare()

        when:
        module.ivy.expectGetMissing()

        then:
        fails("verify")
        failure.assertHasCause("""Could not find some.group:some-artifact:1.0.
Searched in the following locations:
  - ${module.ivy.uri}""")

        when:
        server.resetExpectations()
        module.ivy.expectGetMissing()

        then:
        fails("verify")
        failure.assertHasCause("""Could not find some.group:some-artifact:1.0.
Searched in the following locations:
  - ${module.ivy.uri}""")
    }

    def "reports failure to resolve missing artifacts"() {
        fixture.expectSourceArtifactNotFound("my-sources")
                .expectJavadocArtifactNotFound("my-javadoc")
                .prepare()

        when:
        module.ivy.expectGet()
        def sourceArtifact = module.getArtifact(classifier: "my-sources")
        sourceArtifact.expectGetMissing()
        def javadocArtifact = module.getArtifact(classifier: "my-javadoc")
        javadocArtifact.expectGetMissing()

        then:
        fails("verify")
        failure.assertHasCause("""Could not find some-artifact-1.0-my-sources.jar (some.group:some-artifact:1.0).
Searched in the following locations:
    ${sourceArtifact.uri}""")
        failure.assertHasCause("""Could not find some-artifact-1.0-my-javadoc.jar (some.group:some-artifact:1.0).
Searched in the following locations:
    ${javadocArtifact.uri}""")

        when:
        server.resetExpectations()

        then:
        fails("verify")
        failure.assertHasCause("""Could not find some-artifact-1.0-my-sources.jar (some.group:some-artifact:1.0).
Searched in the following locations:
    ${sourceArtifact.uri}""")
        failure.assertHasCause("""Could not find some-artifact-1.0-my-javadoc.jar (some.group:some-artifact:1.0).
Searched in the following locations:
    ${javadocArtifact.uri}""")
    }

    def "resolves when some artifacts are missing"() {
        fixture.expectSourceArtifact("my-sources")
                .expectJavadocArtifactNotFound("my-javadoc")
                .prepare()

        when:
        module.ivy.expectGet()
        module.getArtifact(classifier: "my-sources").expectGet()
        def javadocArtifact = module.getArtifact(classifier: "my-javadoc")
        javadocArtifact.expectGetMissing()

        then:
        fails("verify")
        failure.assertHasCause("""Could not find some-artifact-1.0-my-javadoc.jar (some.group:some-artifact:1.0).
Searched in the following locations:
    ${javadocArtifact.uri}""")

        when:
        server.resetExpectations()

        then:
        fails("verify")
        failure.assertHasCause("""Could not find some-artifact-1.0-my-javadoc.jar (some.group:some-artifact:1.0).
Searched in the following locations:
    ${javadocArtifact.uri}""")
    }

    def "resolves and recovers from broken artifacts"() {
        given:
        module.artifact(type: "source", classifier: "broken-sources", ext: "jar", conf: "sources")
        module.publish()

        fixture.expectSourceArtifact("my-sources")
                .expectSourceArtifactFailure()
                .expectJavadocArtifactFailure()
                .prepare()

        when:
        module.ivy.expectGet()
        module.getArtifact(classifier: "my-sources").expectGet()
        def brokenSources = module.getArtifact(classifier: "broken-sources")
        brokenSources.expectGetBroken()
        def brokenJavadoc = module.getArtifact(classifier: "my-javadoc")
        brokenJavadoc.expectGetBroken()

        then:
        fails("verify")
        failure.assertHasCause("Could not download some-artifact-1.0-broken-sources.jar (some.group:some-artifact:1.0)")
        failure.assertHasCause("Could not get resource '${brokenSources.uri}'.")
        failure.assertHasCause("Could not GET '${brokenSources.uri}'. Received status code 500 from server: broken")
        failure.assertHasCause("Could not download some-artifact-1.0-my-javadoc.jar (some.group:some-artifact:1.0)")
        failure.assertHasCause("Could not get resource '${brokenJavadoc.uri}'.")
        failure.assertHasCause("Could not GET '${brokenJavadoc.uri}'. Received status code 500 from server: broken")

        when:
        fixture.clearExpectations()
                .expectSourceArtifact("my-sources")
                .expectSourceArtifact("broken-sources")
                .expectJavadocArtifact("my-javadoc")
                .createVerifyTask("verifyFixed")

        and:
        server.resetExpectations()
        // Only the broken artifacts are not cached
        brokenSources.expectGet()
        brokenJavadoc.expectGet()

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
                .requestingSource().requestingJavadoc()
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
