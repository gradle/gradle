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

package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest

/**
 * End-to-end integration tests that drive Gradle dependency resolution against a Maven
 * repository which fails artifact requests with an RFC 9457 Problem Details response
 * ({@code application/problem+json}).
 *
 * <p>These tests exercise the full resolution stack (not just the low-level HTTP client)
 * and demonstrate the console output a user sees when a modern repository (e.g.
 * Nexus 3.75+, Artifactory) rejects a request with a detailed problem body.
 */
class MavenRfc9457RemoteResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        settingsFile << """
            rootProject.name = "root"
        """
        buildFile << """
            repositories {
                maven { url = "${mavenHttpRepo.uri}" }
            }
            configurations { compile }
            dependencies {
                compile 'group:projectA:1.2'
            }
            task retrieve(type: Sync) {
                from configurations.compile
                into 'libs'
            }
        """
    }

    def "surfaces RFC 9457 detail from artifact-quarantine 403 (detail-only overload)"() {
        given:
        def module = mavenHttpRepo.module('group', 'projectA', '1.2').publish()

        when:
        module.pom.expectGet()
        module.artifact.expectGetBroken(403,
            "Artifact 'group:projectA:1.2' is quarantined pending policy review (rule: 'block-untrusted-publishers').")

        then:
        fails "retrieve"
        failure.assertHasCause("Could not download projectA-1.2.jar (group:projectA:1.2)")
        failure.assertHasCause("Could not GET '${module.artifact.uri}'.")
        failure.assertHasCause(
            "Received status code 403 from server: " +
                "Artifact 'group:projectA:1.2' is quarantined pending policy review (rule: 'block-untrusted-publishers')."
        )
    }

    def "surfaces RFC 9457 detail from license-policy 451 (full-payload overload)"() {
        given:
        def module = mavenHttpRepo.module('group', 'projectA', '1.2').publish()

        when:
        module.pom.expectGet()
        module.artifact.expectGetBroken(451, [
            type    : "https://example.com/problems/license-violation",
            title   : "License Policy Violation",
            status  : 451,
            detail  : "The artifact group:projectA:1.2 uses a GPL-3.0 license, which is not permitted by the organization's approved-licenses policy.",
            instance: "/repository/maven-central/group/projectA/1.2/projectA-1.2.jar"
        ])

        then:
        fails "retrieve"
        failure.assertHasCause("Could not download projectA-1.2.jar (group:projectA:1.2)")
        failure.assertHasCause("Could not GET '${module.artifact.uri}'.")
        failure.assertHasCause(
            "Received status code 451 from server: " +
                "The artifact group:projectA:1.2 uses a GPL-3.0 license, which is not permitted by the organization's approved-licenses policy."
        )
    }

    def "surfaces RFC 9457 detail from vulnerability-gate 403 (full-payload overload) and shows it in console output"() {
        given:
        def module = mavenHttpRepo.module('group', 'projectA', '1.2').publish()

        when:
        module.pom.expectGet()
        module.artifact.expectGetBroken(403, [
            type    : "https://example.com/problems/vulnerability-blocked",
            title   : "Vulnerability Policy Violation",
            status  : 403,
            detail  : "Download blocked: group:projectA:1.2 has 2 known critical CVEs (CVE-2025-11111, CVE-2025-22222). Contact security@example.com to request an exception.",
            instance: "/repository/maven-central/group/projectA/1.2/projectA-1.2.jar"
        ])

        then:
        fails "retrieve"

        and: "the detailed RFC 9457 message reaches the user-facing console output"
        failure.assertHasErrorOutput(
            "Received status code 403 from server: Download blocked: group:projectA:1.2 has 2 known critical CVEs (CVE-2025-11111, CVE-2025-22222). Contact security@example.com to request an exception."
        )
    }
}
