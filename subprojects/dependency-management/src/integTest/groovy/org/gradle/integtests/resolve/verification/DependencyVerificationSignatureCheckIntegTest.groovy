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
import org.gradle.security.internal.Fingerprint
import spock.lang.Unroll

import static org.gradle.security.fixtures.SigningFixtures.getValidPublicKeyLongIdHexString
import static org.gradle.security.fixtures.SigningFixtures.signAsciiArmored
import static org.gradle.security.fixtures.SigningFixtures.validPublicKeyHexString
import static org.gradle.security.internal.SecuritySupport.toLongIdHexString

class DependencyVerificationSignatureCheckIntegTest extends AbstractSignatureVerificationIntegrationTest {

    def "doesn't need checksums if signature is verified"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
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

    def "doesn't need checksums if signature is verified and trust using long id"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyLongIdHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyLongIdHexString, "pom", "pom")
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

    def "if signature is verified and checksum is declared in configuration, verify checksum"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
            addChecksum("org:foo:1.0", "sha256", "invalid")
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
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': expected a 'sha256' checksum of 'invalid' but was '20ae575ede776e5e06ee6b168652d11ee23069e92de110fdec13fbeaa5cf3bbc'

This can indicate that a dependency has been compromised. Please carefully verify the checksums."""
    }

    def "fails verification is key is missing"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
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
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""
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
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
        }
        if (stopInBetween) {
            executer.stop()
        }
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""

        where:
        stopInBetween << [false, true]
    }

    @ToBeFixedForInstantExecution
    @Unroll
    def "can verify classified artifacts downloaded in previous builds (stop in between = #stopInBetween)"() {
        def keyring = newKeyRing()
        keyServerFixture.registerPublicKey(keyring.publicKey)
        def pkId = Fingerprint.of(keyring.publicKey)

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
            addTrustedKeyByFileName("org:foo:1.0", "foo-1.0-classy.jar", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
        }
        if (stopInBetween) {
            executer.stop()
        }
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0-classy.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '$pkId' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '$pkId' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""

        where:
        stopInBetween << [false, true]
    }


    def "fails verification is signature is incorrect"() {
        createMetadataFile {
            noMetadataVerification()
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
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
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key 'd7bf96a169f77b28c934ab1614f53f0824875d73' (Gradle Test (This is used for testing the gradle-signing-plugin) <test@gradle.org>) but signature didn't match

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""
    }

    def "doesn't check the same artifact multiple times during a build"() {
        createMetadataFile {
            noMetadataVerification()
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            trust("org", "bar", "1.0")
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
        uncheckedModule("org", "bar", "1.0") {
            dependsOn("org", "foo", "1.0")
        }
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
                implementation "org:bar:1.0"
            }
        """

        when:
        serveValidKey()
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key 'd7bf96a169f77b28c934ab1614f53f0824875d73' (Gradle Test (This is used for testing the gradle-signing-plugin) <test@gradle.org>) but signature didn't match

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""
    }

    def "doesn't check the same parent POM file multiple times during a build"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
            trust("org", "bar", "1.0")
        }

        given:
        javaLibrary()
        uncheckedModule("org", "parent", "1.0") {
            withSignature {
                signAsciiArmored(it)
                if (name.endsWith(".pom")) {
                    // change contents of original file so that the signature doesn't match anymore
                    text = "${text}\n"
                }
            }
        }
        uncheckedModule("org", "foo", "1.0") {
            parent("org", "parent", "1.0")
            withSignature {
                signAsciiArmored(it)
            }
        }
        uncheckedModule("org", "bar", "1.0") {
            parent("org", "parent", "1.0")
        }
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
                implementation "org:bar:1.0"
            }
        """

        when:
        serveValidKey()
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact parent-1.0.pom (org:parent:1.0) in repository 'maven': Artifact was signed with key 'd7bf96a169f77b28c934ab1614f53f0824875d73' (Gradle Test (This is used for testing the gradle-signing-plugin) <test@gradle.org>) but signature didn't match

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""
    }


    def "fails verification is signature is not trusted"() {
        def keyring = newKeyRing()
        keyServerFixture.registerPublicKey(keyring.publicKey)
        def pkId = Fingerprint.of(keyring.publicKey)

        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
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
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '$pkId' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '$pkId' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""
    }

    @Unroll
    def "can verify classified artifacts trusting key #trustedKey"() {
        def keyring = newKeyRing()
        keyServerFixture.registerPublicKey(keyring.publicKey)
        def pkId = Fingerprint.of(keyring.publicKey)

        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKeyByFileName("org:foo:1.0", "foo-1.0-classy.jar", trustedKey)
            addTrustedKey("org:foo:1.0", trustedKey, "pom", "pom")
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
  - On artifact foo-1.0-classy.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '$pkId' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '$pkId' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""

        where:
        trustedKey << [
            validPublicKeyHexString,
            validPublicKeyLongIdHexString
        ]
    }

    def "reasonable error message if key server fails to answer"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
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
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""
    }

    def "can fetch key from different keyserver"() {
        def keyring = newKeyRing()
        def secondServer = new KeyServer(temporaryFolder.createDir("keyserver-${UUID.randomUUID()}"))
        secondServer.registerPublicKey(keyring.publicKey)
        def pkId = toLongIdHexString(keyring.publicKey.keyID)
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
        def pkId = Fingerprint.of(keyring.publicKey)

        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
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
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '$pkId' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '$pkId' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""
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
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""

        when: "publish keys"
        keyServerFixture.withDefaultSigningKey()
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""

        when: "refreshes the keys"
        succeeds ":compileJava", "--refresh-keys"

        then:
        noExceptionThrown()
    }


    @ToBeFixedForInstantExecution
    def "cache takes ignored keys into consideration"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
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
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""

        when: "ignore key"
        replaceMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addGloballyIgnoredKey("14f53f0824875d73")
        }
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': checksum is missing from verification metadata.
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': checksum is missing from verification metadata."""

        when: "doesn't ignore key anymore"
        replaceMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
        }
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
"""

        when: "ignore key only for artifact"
        replaceMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addIgnoredKeyByFileName("org:foo:1.0", "foo-1.0.jar", "14f53f0824875d73")
        }
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
  - On artifact foo-1.0.jar (org:foo:1.0) multiple problems reported:
      - in repository 'maven': artifact was signed but all keys were ignored
      - in repository 'maven': checksum is missing from verification metadata."""

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
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key 'd7bf96a169f77b28c934ab1614f53f0824875d73' (Gradle Test (This is used for testing the gradle-signing-plugin) <test@gradle.org>) but signature didn't match"""
    }

    @ToBeFixedForInstantExecution
    def "caching takes trusted keys into account"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
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
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key 'd7bf96a169f77b28c934ab1614f53f0824875d73' (Gradle Test (This is used for testing the gradle-signing-plugin) <test@gradle.org>) and passed verification but the key isn't in your trusted keys list.

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""
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
  - On artifact foo-1.0.jar (org:foo:1.0) multiple problems reported:
      - in repository 'maven': artifact wasn't signed
      - in repository 'maven': expected a 'sha256' checksum of 'nope' but was '20ae575ede776e5e06ee6b168652d11ee23069e92de110fdec13fbeaa5cf3bbc'
  - On artifact foo-1.0.pom (org:foo:1.0) multiple problems reported:
      - in repository 'maven': artifact wasn't signed
      - in repository 'maven': expected a 'sha256' checksum of 'nope' but was 'f331cce36f6ce9ea387a2c8719fabaf67dc5a5862227ebaa13368ff84eb69481'

This can indicate that a dependency has been compromised. Please carefully verify the checksums."""
    }

    def "can ignore a key and fallback to checksum verification"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addGloballyIgnoredKey(getValidPublicKeyLongIdHexString())
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
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': checksum is missing from verification metadata.
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': checksum is missing from verification metadata."""
    }

    def "can ignore a key using full fingerprint and fallback to checksum verification"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addGloballyIgnoredKey(SigningFixtures.validPublicKeyHexString)
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
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': checksum is missing from verification metadata.
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': checksum is missing from verification metadata."""
    }

    def "can ignore a key for a specific artifact and fallback to checksum verification"() {
        // we tamper the jar, so the verification of the jar would fail, but not the POM
        keyServerFixture.withDefaultSigningKey()
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addIgnoredKeyByFileName("org:foo:1.0", "foo-1.0.jar", validPublicKeyHexString)
        }

        given:
        javaLibrary()
        def module = uncheckedModule("org", "foo", "1.0") {
            withSignature {
                signAsciiArmored(it)
            }
        }
        module.artifactFile.bytes = [0, 1, 2]

        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        fails ":compileJava"

        then:
        // jar file fails because it doesn't have any checksum declared, despite ignoring the key, which is what we want
        // and pom file fails because we didn't trust the key
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key 'd7bf96a169f77b28c934ab1614f53f0824875d73' (Gradle Test (This is used for testing the gradle-signing-plugin) <test@gradle.org>) and passed verification but the key isn't in your trusted keys list.
  - On artifact foo-1.0.jar (org:foo:1.0) multiple problems reported:
      - in repository 'maven': artifact was signed but all keys were ignored
      - in repository 'maven': checksum is missing from verification metadata.

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""

    }

    def "can ignore a key by long id for a specific artifact and fallback to checksum verification"() {
        // we tamper the jar, so the verification of the jar would fail, but not the POM
        keyServerFixture.withDefaultSigningKey()
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addIgnoredKeyByFileName("org:foo:1.0", "foo-1.0.jar", validPublicKeyLongIdHexString)
        }

        given:
        javaLibrary()
        def module = uncheckedModule("org", "foo", "1.0") {
            withSignature {
                signAsciiArmored(it)
            }
        }
        module.artifactFile.bytes = [0, 1, 2]

        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        fails ":compileJava"

        then:
        // jar file fails because it doesn't have any checksum declared, despite ignoring the key, which is what we want
        // and pom file fails because we didn't trust the key
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key 'd7bf96a169f77b28c934ab1614f53f0824875d73' (Gradle Test (This is used for testing the gradle-signing-plugin) <test@gradle.org>) and passed verification but the key isn't in your trusted keys list.
  - On artifact foo-1.0.jar (org:foo:1.0) multiple problems reported:
      - in repository 'maven': artifact was signed but all keys were ignored
      - in repository 'maven': checksum is missing from verification metadata.

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""

    }

    def "passes verification if an artifact is signed with multiple keys and one of them is ignored"() {
        def keyring = newKeyRing()
        keyServerFixture.registerPublicKey(keyring.publicKey)
        def pkId = toLongIdHexString(keyring.publicKey.keyID)
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            // only the new keyring key is published and available
            addGloballyIgnoredKey(validPublicKeyLongIdHexString)
            addTrustedKey("org:foo:1.0", pkId)
            addTrustedKey("org:foo:1.0", pkId, "pom", "pom")
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
        succeeds ":compileJava"

        then:
        noExceptionThrown()
    }

    def "can collect multiple errors for single dependency"() {
        def keyring = newKeyRing()
        keyServerFixture.registerPublicKey(keyring.publicKey)
        def pkId = Fingerprint.of(keyring.publicKey)

        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
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
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Multiple signature verification errors found:
      - Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
      - Artifact was signed with key '${pkId}' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Multiple signature verification errors found:
      - Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
      - Artifact was signed with key '${pkId}' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""
    }

    def "can declare globally trusted keys"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addGloballyTrustedKey(validPublicKeyHexString, "org")
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

    @Unroll
    def "can mix globally trusted keys and artifact specific keys (trust artifact key = #addLocalKey)"() {
        def keyring = newKeyRing()
        keyServerFixture.registerPublicKey(keyring.publicKey)
        def pkId = Fingerprint.of(keyring.publicKey)

        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addGloballyTrustedKey(validPublicKeyHexString, "o.*", "foo", null, null, true)
            if (addLocalKey) {
                addTrustedKey("org:foo:1.0", pkId.toString())
                addTrustedKey("org:foo:1.0", pkId.toString(), "pom", "pom")
            }
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
        serveValidKey()
        if (addLocalKey) {
            succeeds ":compileJava"
        } else {
            fails ":compileJava"
        }

        then:
        if (addLocalKey) {
            outputContains("Dependency verification is an incubating feature.")
        } else {
            failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '${pkId}' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '${pkId}' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""
        }

        where:
        addLocalKey << [true, false]
    }

    def "can read public keys from keyring"() {
        // key will not be published on the server fixture but available locally
        def keyring = newKeyRing()
        def pkId = toLongIdHexString(keyring.publicKey.keyID)

        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", pkId)
            addTrustedKey("org:foo:1.0", pkId, "pom", "pom")
        }
        keyring.writePublicKeyRingTo(file("gradle/verification-keyring.gpg"))

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

    }
}
