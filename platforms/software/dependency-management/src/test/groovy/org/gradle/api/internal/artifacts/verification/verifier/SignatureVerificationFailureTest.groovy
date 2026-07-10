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

package org.gradle.api.internal.artifacts.verification.verifier

import org.gradle.security.internal.PublicKeyService
import spock.lang.Issue
import spock.lang.Specification

@Issue("https://github.com/gradle/gradle/issues/20100")
class SignatureVerificationFailureTest extends Specification {

    def "no note when there is no trusted-keys context for the artifact"() {
        expect:
        note(SignatureVerificationFailure.TrustedKeys.empty()).empty
    }

    def "explicit note when trust is configured but none applies to the artifact"() {
        expect:
        note(trustedKeys([], [])) == " (no other keys are already trusted for module 'org:foo' or group 'org')"
    }

    def "reports module-scoped trusted keys"() {
        expect:
        note(trustedKeys([], ["KEY1"])) == " (1 other key is already trusted for module 'org:foo')"
    }

    def "reports group-scoped trusted keys with pluralization"() {
        expect:
        note(trustedKeys(["KEY1", "KEY2"], [])) == " (2 other keys are already trusted for group 'org')"
    }

    def "reports module-scoped before group-scoped when both are present"() {
        expect:
        note(trustedKeys(["G1"], ["M1"])) == " (1 other key is already trusted for module 'org:foo'; 1 other key is already trusted for group 'org')"
    }

    private static SignatureVerificationFailure.TrustedKeys trustedKeys(List<String> groupScoped, List<String> moduleScoped) {
        new SignatureVerificationFailure.TrustedKeys("org", "foo", groupScoped as Set, moduleScoped as Set)
    }

    private String note(SignatureVerificationFailure.TrustedKeys trustedKeys) {
        new SignatureVerificationFailure(new File("foo-1.0.jar"), new File("foo-1.0.jar.asc"), [:], Stub(PublicKeyService), trustedKeys)
            .otherTrustedKeysNote()
    }
}
