/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.resolve.verification

import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.security.fixtures.KeyServer
import org.gradle.security.fixtures.SigningFixtures
import spock.lang.Unroll

import static org.gradle.security.fixtures.SigningFixtures.signAsciiArmored
import static org.gradle.security.internal.SecuritySupport.toHexString

class DependencyVerificationSignatureCheckIntegTest extends AbstractSignatureVerificationIntegrationTest {

    def "doesn't need checksums if signature is verified"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString, "pom", "pom")
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            withSignature {
                signAsciiArmored(it)
            }
        }
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        serveValidKey()
        succeeds ":compileJava"

        then:
        outputContains("Dependency verification is an incubating feature.")
    }

    def "fails verification is key is missing"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString, "pom", "pom")
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            withSignature {
                signAsciiArmored(it)
            }
        }
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0): Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
  - On artifact foo-1.0.pom (org:foo:1.0): Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
This can indicate that a dependency has been compromised. Please verify carefully the checksums."""
    }

    @ToBeFixedForInstantExecution
    @Unroll
    def "can verify signature for artifacts downloaded in a previous build (stop in between = #stopInBetween)"() {
        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            withSignature {
                signAsciiArmored(it)
            }
        }
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        succeeds ":compileJava"

        then:
        outputDoesNotContain "Dependency verification is an incubating feature."

        when:
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString, "pom", "pom")
        }
        if (stopInBetween) {
            executer.stop()
        }
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0): Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
  - On artifact foo-1.0.pom (org:foo:1.0): Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
This can indicate that a dependency has been compromised. Please verify carefully the checksums."""

        where:
        stopInBetween << [false, true]
    }

    @ToBeFixedForInstantExecution
    @Unroll
    def "can verify classified artifacts downloaded in previous builds (stop in between = #stopInBetween)"() {
        def keyring = newKeyRing()
        keyServerFixture.registerPublicKey(keyring.publicKey)
        def pkId = toHexString(keyring.publicKey.keyID)

        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            artifact(classifier: 'classy')
            withSignature {
                keyring.sign(it)
            }
        }
        buildFile << """
            dependencies {
                implementation "org:foo:1.0:classy"
            }
        """

        when:
        succeeds ":compileJava"

        then:
        outputDoesNotContain "Dependency verification is an incubating feature."

        when:
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKeyByFileName("org:foo:1.0", "foo-1.0-classy.jar", SigningFixtures.validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString, "pom", "pom")
        }
        if (stopInBetween) {
            executer.stop()
        }
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0-classy.jar (org:foo:1.0): Artifact was signed with key '$pkId' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.
  - On artifact foo-1.0.pom (org:foo:1.0): Artifact was signed with key '$pkId' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.
This can indicate that a dependency has been compromised. Please verify carefully the checksums."""

        where:
        stopInBetween << [false, true]
    }


    def "fails verification is signature is incorrect"() {
        createMetadataFile {
            noMetadataVerification()
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString)
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            withSignature {
                signAsciiArmored(it)
                if (name.endsWith(".jar")) {
                    // change contents of original file so that the signature doesn't match anymore
                    bytes = [0, 1, 2, 3]
                }
            }
        }
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        serveValidKey()
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0): Artifact was signed with key '14f53f0824875d73' but signature didn't match
This can indicate that a dependency has been compromised. Please verify carefully the checksums."""
    }


    def "fails verification is signature is not trusted"() {
        def keyring = newKeyRing()
        keyServerFixture.registerPublicKey(keyring.publicKey)
        def pkId = toHexString(keyring.publicKey.keyID)

        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString, "pom", "pom")
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            withSignature {
                keyring.sign(it)
            }
        }
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0): Artifact was signed with key '$pkId' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.
  - On artifact foo-1.0.pom (org:foo:1.0): Artifact was signed with key '$pkId' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.
This can indicate that a dependency has been compromised. Please verify carefully the checksums."""
    }

    def "can verify classified artifacts"() {
        def keyring = newKeyRing()
        keyServerFixture.registerPublicKey(keyring.publicKey)
        def pkId = toHexString(keyring.publicKey.keyID)

        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKeyByFileName("org:foo:1.0", "foo-1.0-classy.jar", SigningFixtures.validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString, "pom", "pom")
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            artifact(classifier: 'classy')
            withSignature {
                keyring.sign(it)
            }
        }
        buildFile << """
            dependencies {
                implementation "org:foo:1.0:classy"
            }
        """

        when:
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0-classy.jar (org:foo:1.0): Artifact was signed with key '$pkId' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.
  - On artifact foo-1.0.pom (org:foo:1.0): Artifact was signed with key '$pkId' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.
This can indicate that a dependency has been compromised. Please verify carefully the checksums."""
    }

    def "reasonable error message if key server fails to answer"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString, "pom", "pom")
        }

        given:
        keyServerFixture.stop()
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            withSignature {
                signAsciiArmored(it)
            }
        }
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0): Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
  - On artifact foo-1.0.pom (org:foo:1.0): Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
This can indicate that a dependency has been compromised. Please verify carefully the checksums."""
    }

    def "can fetch key from different keyserver"() {
        def keyring = newKeyRing()
        def secondServer = new KeyServer(temporaryFolder.createDir("keyserver-${UUID.randomUUID()}"))
        secondServer.registerPublicKey(keyring.publicKey)
        def pkId = toHexString(keyring.publicKey.keyID)
        secondServer.start()
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            keyServer(secondServer.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", pkId)
            addTrustedKey("org:foo:1.0", pkId, "pom", "pom")
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            withSignature {
                keyring.sign(it)
            }
        }
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        succeeds ":compileJava"

        then:
        outputContains("Dependency verification is an incubating feature.")

        cleanup:
        secondServer.stop()
    }

    def "must verify all signatures"() {
        def keyring = newKeyRing()
        keyServerFixture.withDefaultSigningKey()
        keyServerFixture.registerPublicKey(keyring.publicKey)
        def pkId = toHexString(keyring.publicKey.keyID)

        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString, "pom", "pom")
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            withSignature {
                keyring.sign(it, [(SigningFixtures.validSecretKey): SigningFixtures.validPassword])
            }
        }
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0): Artifact was signed with key '$pkId' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.
  - On artifact foo-1.0.pom (org:foo:1.0): Artifact was signed with key '$pkId' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.
This can indicate that a dependency has been compromised. Please verify carefully the checksums."""
    }

    @ToBeFixedForInstantExecution
    def "caches missing keys"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString, "pom", "pom")
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            withSignature {
                signAsciiArmored(it)
            }
        }
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0): Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
  - On artifact foo-1.0.pom (org:foo:1.0): Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
This can indicate that a dependency has been compromised. Please verify carefully the checksums."""

        when: "publish keys"
        keyServerFixture.withDefaultSigningKey()
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0): Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
  - On artifact foo-1.0.pom (org:foo:1.0): Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
This can indicate that a dependency has been compromised. Please verify carefully the checksums."""

        when: "refreshes the keys"
        succeeds ":compileJava", "--refresh-keys"

        then:
        noExceptionThrown()
    }

    // This test exercises the fact that the signature cache is aware
    // of changes of the artifact
    @ToBeFixedForInstantExecution
    def "can detect tampered file between builds"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString, "pom", "pom")
        }
        keyServerFixture.withDefaultSigningKey()

        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            withSignature {
                signAsciiArmored(it)
            }
        }
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        succeeds ":compileJava"

        then:
        noExceptionThrown()

        when:
        def group = new File(CacheLayout.FILE_STORE.getPath(metadataCacheDir), "org")
        def module = new File(group, "foo")
        def version = new File(module, "1.0")
        def originHash = new File(version, "16e066e005a935ac60f06216115436ab97c5da02")
        def artifactFile = new File(originHash, "foo-1.0.jar")
        artifactFile.text = "tampered"
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0): Artifact was signed with key '14f53f0824875d73' but signature didn't match"""
    }

    @ToBeFixedForInstantExecution
    def "caching takes trusted keys into account"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString, "pom", "pom")
        }
        keyServerFixture.withDefaultSigningKey()

        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            withSignature {
                signAsciiArmored(it)
            }
        }
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        succeeds ":compileJava"

        then:
        noExceptionThrown()

        when:
        replaceMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString, "pom", "pom")
        }
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - Artifact foo-1.0.jar (org:foo:1.0) checksum is missing from verification metadata."""
    }

    def "unsigned artifacts require checksum verification"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addChecksum("org:foo:1.0", "sha256", "nope")
            addChecksum("org:foo:1.0", "sha256", "nope", "pom", "pom")
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0")
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0): expected a 'sha256' checksum of 'nope' but was '20ae575ede776e5e06ee6b168652d11ee23069e92de110fdec13fbeaa5cf3bbc'
  - On artifact foo-1.0.pom (org:foo:1.0): expected a 'sha256' checksum of 'nope' but was 'f331cce36f6ce9ea387a2c8719fabaf67dc5a5862227ebaa13368ff84eb69481'
This can indicate that a dependency has been compromised. Please verify carefully the checksums."""
    }
}
