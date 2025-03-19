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
                    artifactUrls('http://does-not-exist.com')
                }
            }
            dependencies {
                deps("org:foo:1.0")
            }
        """

        expect:
        module.pom.expectGet()
        module.artifact.expectGetMissing()
        fails("resolve")
        failure.assertHasErrorOutput("does-not-exist.com") // Cannot check whole message, as it differs between OS

        and:
        module.artifact.expectGetMissing()
        succeeds("resolveLenient")
        outputContains("[]")
    }

    private void withRepo() {
        buildFile << """
            repositories {
                maven { url = '${mavenHttpRepo.uri}' }
            }
        """
    }
}
