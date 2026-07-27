/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.api.internal.artifacts.verification.DependencyVerificationFixture
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import spock.lang.Issue

/**
 * Tests the behavior of {@link org.gradle.api.artifacts.ArtifactView} when it is configured to be lenient.
 */
class LenientArtifactViewIntegrationTest extends AbstractHttpDependencyResolutionTest {

    private final DependencyVerificationFixture verificationFile = new DependencyVerificationFixture(
        file("gradle/verification-metadata.xml")
    )

    def setup() {
        buildFile """
            plugins {
                id("jvm-ecosystem")
            }
            configurations {
                dependencyScope("deps")
                resolvable("res") {
                    extendsFrom(deps)
                }
            }
            tasks.register("resolve") {
                def files = configurations.res.incoming.files
                doLast {
                    println files*.name
                }
            }
            tasks.register("resolveLenient") {
                def files = configurations.res.incoming.artifactView {
                    lenient = true
                }.files
                doLast {
                    println files*.name
                }
            }
        """
    }

    def "lenient artifact view permits non-existent dependency"() {
        def module = mavenHttpRepo.module("org", "foo")

        withRepo()
        buildFile """
            dependencies {
                deps("org:foo:1.0")
            }
        """

        expect:
        module.pom.expectGetMissing()
        fails("resolve")
        failure.assertHasCause("Could not find org:foo:1.0")

        and:
        module.pom.expectGetMissing()
        succeeds("resolveLenient")
        outputContains("[]")
    }

    def "lenient artifact view permits non-existent artifact"() {
        def module = mavenHttpRepo.module("org", "foo").publish()

        withRepo()
        buildFile """
            dependencies {
                deps("org:foo:1.0")
            }
        """

        expect:
        module.pom.expectGet()
        module.artifact.expectGetMissing()
        fails("resolve")
        failure.assertHasCause("Could not find foo-1.0.jar")

        succeeds("resolveLenient")
        outputContains("[]")
    }

    def "lenient artifact view permits broken artifact"() {
        def module = mavenHttpRepo.module("org", "foo").publish()

        withRepo()
        buildFile """
            dependencies {
                deps("org:foo:1.0")
            }
        """

        expect:
        module.pom.expectGet()
        module.artifact.expectGetBroken()
        fails("resolve")
        failure.assertHasCause("Could not download foo-1.0.jar")

        module.artifact.expectGetBroken()
        succeeds("resolveLenient")
        outputContains("[]")
    }

    def "lenient artifact view permits unauthorized artifact"() {
        def module = mavenHttpRepo.module("org", "foo").publish()

        withRepo()
        buildFile """
            dependencies {
                deps("org:foo:1.0")
            }
        """

        expect:
        module.pom.expectGet()
        module.artifact.expectGetUnauthorized()
        fails("resolve")
        failure.assertHasCause("Could not download foo-1.0.jar")

        and:
        module.artifact.expectGetUnauthorized()
        succeeds("resolveLenient")
        outputContains("[]")
    }

    def "lenient artifact does not permit metadata verification failure"() {
        def module = mavenHttpRepo.module("org", "foo").publish()

        verificationFile.createMetadataFile {
            addChecksum(module, kind, "jar", "jar", null, null)
            addChecksum("org:foo:1.0", kind, "invalid", "pom", "pom", null, null)
        }

        withRepo()
        buildFile """
            dependencies {
                deps("org:foo:1.0")
            }
        """

        expect:
        module.pom.expectGet()
        module.artifact.expectGet()
        fails("resolve")
        failure.assertHasErrorOutput("One artifact failed verification: foo-1.0.pom (org:foo:1.0) from repository maven")

        and:
        fails("resolveLenient")
        failure.assertHasErrorOutput("One artifact failed verification: foo-1.0.pom (org:foo:1.0) from repository maven")

        where:
        kind << ["md5", "sha1", "sha256", "sha512"]
    }

    def "lenient artifact does not permit artifact verification failure"() {
        def module = mavenHttpRepo.module("org", "foo").publish()

        verificationFile.createMetadataFile {
            addChecksum(module, kind, "pom", "pom", null, null)
            addChecksum("org:foo:1.0", kind, "invalid", "jar", "jar", null, null)
        }

        withRepo()
        buildFile """
            dependencies {
                deps("org:foo:1.0")
            }
        """

        expect:
        module.pom.expectGet()
        module.artifact.expectGet()
        fails("resolve")
        failure.assertHasErrorOutput("One artifact failed verification: foo-1.0.jar (org:foo:1.0) from repository maven")

        and:
        fails("resolveLenient")
        failure.assertHasErrorOutput("One artifact failed verification: foo-1.0.jar (org:foo:1.0) from repository maven")

        where:
        kind << ["md5", "sha1", "sha256", "sha512"]
    }

    // This may not be desired behavior.
    def "lenient artifact permits premature end of content"() {
        def module = mavenHttpRepo.module("org", "foo").publish()

        withRepo()
        buildFile """
            dependencies {
                deps("org:foo:1.0")
            }
        """

        expect:
        module.pom.expectGet()
        mavenHttpRepo.server.expectGetEarlyClose(module.artifact.path)
        fails("resolve")
        failure.assertHasErrorOutput("Premature end of Content-Length delimited message body")

        and:
        mavenHttpRepo.server.expectGetEarlyClose(module.artifact.path)
        succeeds("resolveLenient")
        outputContains("[]")
    }

    // This may not be desired behavior.
    def "lenient artifact permits invalid repo URL"() {
        def module = mavenHttpRepo.module("org", "foo").publish()

        buildFile """
            repositories {
                maven {
                    url = "${mavenHttpRepo.uri}"
                    artifactUrls('http://does-not-exist.invalid')
                }
            }
            dependencies {
                deps("org:foo:1.0")
            }
        """

        expect:
        module.pom.expectGet()
        module.artifact.expectGetMissing()
        executer.expectDocumentedDeprecationWarning("The MavenArtifactRepository.artifactUrls(Object...) method has been deprecated. This is scheduled to be removed in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_maven_artifact_urls")
        fails("resolve")
        failure.assertHasErrorOutput("does-not-exist.invalid") // Cannot check whole message, as it differs between OS

        and:
        module.artifact.expectGetMissing()
        executer.expectDocumentedDeprecationWarning("The MavenArtifactRepository.artifactUrls(Object...) method has been deprecated. This is scheduled to be removed in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_maven_artifact_urls")
        succeeds("resolveLenient")
        outputContains("[]")
    }

    @Issue("https://github.com/gradle/gradle/issues/36284")
    def "can read failure messages from lenient artifact view when failing module's reverse-dependency graph contains a cycle"() {
        // Two transitive modules (modB and modD) directly depend on a missing module ("broken").
        // The four cycle modules (modA -> modB -> modC -> modD -> modA) form a cycle in the
        // resolved graph. With this layout, modB and modD are the directDependees of broken,
        // and modD's only path back to root passes through modC -> modB -> modA, which the
        // pre-fix DependencyGraphPathResolver could not compute correctly because modC was
        // popped from the worklist before modB had been resolved.
        def broken = mavenHttpRepo.module("org", "broken", "1.0")
        def modA = mavenHttpRepo.module("org", "mod-a", "1.0")
            .dependsOn("org", "mod-b", "1.0")
            .publish()
        def modB = mavenHttpRepo.module("org", "mod-b", "1.0")
            .dependsOn("org", "mod-c", "1.0")
            .dependsOn("org", "broken", "1.0")
            .publish()
        def modC = mavenHttpRepo.module("org", "mod-c", "1.0")
            .dependsOn("org", "mod-d", "1.0")
            .publish()
        def modD = mavenHttpRepo.module("org", "mod-d", "1.0")
            .dependsOn("org", "mod-a", "1.0")
            .dependsOn("org", "broken", "1.0")
            .publish()

        withRepo()
        buildFile """
            dependencies {
                deps("org:mod-a:1.0")
            }
            tasks.register("printFailures") {
                def failures = configurations.res.incoming.artifactView {
                    lenient = true
                }.artifacts.failures
                doLast {
                    failures.each { println(it.message) }
                }
            }
        """

        when:
        modA.pom.expectGet()
        modB.pom.expectGet()
        modC.pom.expectGet()
        modD.pom.expectGet()
        broken.pom.expectGetMissing()
        modA.artifact.expectGet()
        modB.artifact.expectGet()
        modC.artifact.expectGet()
        modD.artifact.expectGet()
        succeeds("printFailures")

        then:
        outputContains("Could not find org:broken:1.0.")
    }

    private void withRepo() {
        buildFile << """
            repositories {
                maven { url = '${mavenHttpRepo.uri}' }
            }
        """
    }
}
