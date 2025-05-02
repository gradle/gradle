/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.security.internal

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

class SecuritySupportSpec extends Specification {
    @Rule
    TestNameTestDirectoryProvider tempDir = new TestNameTestDirectoryProvider(getClass())

    def "strips data from saved keys on read"() {
        given:
        def keyringFile = new File(this.class.getResource("/keyrings/valid-with-extra-metadata.keys").getFile())

        when:
        def keyrings = SecuritySupport.loadKeyRingFile(keyringFile)

        then:
        keyrings.size() == 5
        keyrings.forEach { keyRing ->
            keyRing.publicKeys.forEachRemaining { publicKey ->
                assert publicKey.getUserAttributes().size() == 0
                assert publicKey.signatures.size() == publicKey.keySignatures.size()
            }
            assert keyRing.publicKey.userIDs.size() <= 1
        }
        keyrings[0].publicKey.userIDs[0] == "Gradle Inc. <info@gradle.com>"
        keyrings[1].publicKey.userIDs[0] == "Stian Soiland <stain@s11.no>"
        keyrings[2].publicKey.userIDs[0] == "Gradle <marc@gradle.com>"
        keyrings[3].publicKey.userIDs[0] == "�amonn McManus <eamonn@mcmanus.net>"
        keyrings[4].publicKey.userIDs.size() == 0
    }

    @Issue("https://github.com/gradle/gradle/issues/28400")
    def "can read compressed data from signature"() {
        given:
        def signatureFile = tempDir.file("signature.asc") << """
-----BEGIN PGP MESSAGE-----
Version: BCPG v1.69

owCIXgQAFgIABgUCZKcKCQAKCRCXjfpT5HomLAeHAPkBUtWmJCZotXuJ3x/2w1Db
eoNBsw/CMG9geR34Tvuo3AEAqDsvoIcyXD+860T9nxU+gEydpr9/P1LP1MoFL1Lg
4QE=
=f2hW
-----END PGP MESSAGE-----"""

        when:
        def signatureList = SecuritySupport.readSignatures(signatureFile)

        then:
        noExceptionThrown()
        signatureList.size() == 1
    }
}
