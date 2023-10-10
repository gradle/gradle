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

import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.security.fixtures.KeyServer
import org.gradle.security.fixtures.SigningFixtures
import org.gradle.security.internal.Fingerprint
import spock.lang.Issue

import java.util.concurrent.TimeUnit

import static org.gradle.security.fixtures.SigningFixtures.getValidPublicKeyLongIdHexString
import static org.gradle.security.fixtures.SigningFixtures.signAsciiArmored
import static org.gradle.security.fixtures.SigningFixtures.validPublicKeyHexString
import static org.gradle.security.internal.SecuritySupport.toHexString

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

        then:
        succeeds ":compileJava"
    }

    def "if signature is verified and checksum is declared in configuration, verify checksum (terse output=#terse)"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
            addChecksum("org:foo:1.0", "sha256", "invalid")
        }

        given:
        terseConsoleOutput(terse)
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

        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':compileClasspath'
One artifact failed verification: foo-1.0.jar (org:foo:1.0) from repository maven
This can indicate that a dependency has been compromised. Please carefully verify the checksums."""

            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': expected a 'sha256' checksum of 'invalid' but was 'f46001e8577ce4fdaf4d1f9aed03311c581b08f9e82bf2406e70553101680212'

This can indicate that a dependency has been compromised. Please carefully verify the checksums."""
        }
        assertConfigCacheDiscarded()

        where:
        terse << [true, false]
    }

    def "fails verification if key is (terse output=#terse)"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
        }

        given:
        terseConsoleOutput(terse)
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
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':compileClasspath'
2 artifacts failed verification:
  - foo-1.0.jar (org:foo:1.0) from repository maven
  - foo-1.0.pom (org:foo:1.0) from repository maven
This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""

            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""
        }
        assertConfigCacheDiscarded()
        where:
        terse << [true, false]
    }

    def "can verify signature for artifacts downloaded in a previous build (stop in between = #stopInBetween)"() {
        given:
        terseConsoleOutput(false)
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

        expect:
        succeeds ":compileJava"

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

    def "can verify classified artifacts downloaded in previous builds (stop in between = #stopInBetween)"() {
        def keyring = newKeyRing()
        keyServerFixture.registerPublicKey(keyring.publicKey)
        def pkId = Fingerprint.of(keyring.publicKey)

        given:
        terseConsoleOutput(false)
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

        expect:
        succeeds ":compileJava"

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

If the artifacts are trustworthy, you will need to update the gradle/verification-metadata.xml file. ${docsUrl}"""

        where:
        stopInBetween << [false, true]
    }

    def "fails verification if signature is incorrect (terse output=#terse)"() {
        createMetadataFile {
            noMetadataVerification()
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
        }

        given:
        terseConsoleOutput(terse)
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            withSignature {
                signAsciiArmored(it)
                if (name.endsWith(".jar")) {
                    // change contents of original file so that the signature doesn't match anymore
                    tamperWithFile(it)
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
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':compileClasspath'
One artifact failed verification: foo-1.0.jar (org:foo:1.0) from repository maven
This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""

            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key 'd7bf96a169f77b28c934ab1614f53f0824875d73' (Gradle Test (This is used for testing the gradle-signing-plugin) <test@gradle.org>) but signature didn't match

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""
        }
        assertConfigCacheDiscarded()
        where:
        terse << [true, false]
    }

    def "doesn't report failures when exporting keys"() {
        createMetadataFile {
            noMetadataVerification()
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
        }

        given:
        terseConsoleOutput(true)
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            withSignature {
                signAsciiArmored(it)
                if (name.endsWith(".jar")) {
                    // change contents of original file so that the signature doesn't match anymore
                    tamperWithFile(it)
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

        then:
        succeeds ":compileJava", "--export-keys"
        result.assertNotOutput("A verification file was generated but some problems were discovered")
    }


    def "doesn't check the same artifact multiple times during a build (terse output=#terse)"() {
        createMetadataFile {
            noMetadataVerification()
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            trust("org", "bar", "1.0")
        }

        given:
        terseConsoleOutput(terse)
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            withSignature {
                signAsciiArmored(it)
                if (name.endsWith(".jar")) {
                    // change contents of original file so that the signature doesn't match anymore
                    tamperWithFile(it)
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
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':compileClasspath'
One artifact failed verification: foo-1.0.jar (org:foo:1.0) from repository maven
This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""

            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key 'd7bf96a169f77b28c934ab1614f53f0824875d73' (Gradle Test (This is used for testing the gradle-signing-plugin) <test@gradle.org>) but signature didn't match

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""
        }
        assertConfigCacheDiscarded()
        where:
        terse << [true, false]
    }

    def "doesn't check the same parent POM file multiple times during a build  (terse output=#terse)"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
            trust("org", "bar", "1.0")
        }

        given:
        terseConsoleOutput(terse)
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
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':compileClasspath'
One artifact failed verification: parent-1.0.pom (org:parent:1.0) from repository maven
This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""

            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact parent-1.0.pom (org:parent:1.0) in repository 'maven': Artifact was signed with key 'd7bf96a169f77b28c934ab1614f53f0824875d73' (Gradle Test (This is used for testing the gradle-signing-plugin) <test@gradle.org>) but signature didn't match

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""
        }
        assertConfigCacheDiscarded()
        where:
        terse << [true, false]
    }

    def "fails verification if signature is not trusted (terse output=#terse)"() {
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
        terseConsoleOutput(terse)
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
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':compileClasspath'
2 artifacts failed verification:
  - foo-1.0.jar (org:foo:1.0) from repository maven
  - foo-1.0.pom (org:foo:1.0) from repository maven"""

            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '$pkId' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '$pkId' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.

If the artifacts are trustworthy, you will need to update the gradle/verification-metadata.xml file. ${docsUrl}"""
        }
        assertConfigCacheDiscarded()
        where:
        terse << [true, false]
    }

    def "can verify classified artifacts trusting key #trustedKey"() {
        def keyring = newKeyRing()
        keyServerFixture.registerPublicKey(keyring.publicKey)
        def pkId = Fingerprint.of(keyring.publicKey)

        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKeyByFileName("org:foo:1.0", "foo-1.0-classy.jar", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
        }

        given:
        terseConsoleOutput(false)
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

If the artifacts are trustworthy, you will need to update the gradle/verification-metadata.xml file. ${docsUrl}"""
        assertConfigCacheDiscarded()
    }

    def "reasonable error message if key server fails to answer (terse output=#terse)"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
        }

        given:
        terseConsoleOutput(terse)
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
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':compileClasspath'
2 artifacts failed verification:
  - foo-1.0.jar (org:foo:1.0) from repository maven
  - foo-1.0.pom (org:foo:1.0) from repository maven"""

            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""
        }
        assertConfigCacheDiscarded()
        where:
        terse << [true, false]
    }

    def "can fetch key from different keyserver"() {
        def keyring = newKeyRing()
        def secondServer = new KeyServer(temporaryFolder.createDir("keyserver-${UUID.randomUUID()}"))
        secondServer.registerPublicKey(keyring.publicKey)
        def pkId = toHexString(keyring.publicKey.fingerprint)
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

        expect:
        succeeds ":compileJava"

        cleanup:
        secondServer.stop()
    }

    def "must verify all signatures (terse output=#terse)"() {
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
        terseConsoleOutput(terse)
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
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':compileClasspath'
2 artifacts failed verification:
  - foo-1.0.jar (org:foo:1.0) from repository maven
  - foo-1.0.pom (org:foo:1.0) from repository maven"""

            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '$pkId' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '$pkId' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.

If the artifacts are trustworthy, you will need to update the gradle/verification-metadata.xml file. ${docsUrl}"""
        }
        assertConfigCacheDiscarded()
        where:
        terse << [true, false]
    }

    def "caches missing keys (terse output=#terse)"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString, "pom", "pom")
        }

        given:
        terseConsoleOutput(terse)
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
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':compileClasspath'
2 artifacts failed verification:
  - foo-1.0.jar (org:foo:1.0) from repository maven
  - foo-1.0.pom (org:foo:1.0) from repository maven"""

            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""
        }
        assertConfigCacheDiscarded()
        when: "publish keys"
        keyServerFixture.withDefaultSigningKey()
        fails ":compileJava"

        then:
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':compileClasspath'
2 artifacts failed verification:
  - foo-1.0.jar (org:foo:1.0) from repository maven
  - foo-1.0.pom (org:foo:1.0) from repository maven"""

            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""
        }
        assertConfigCacheDiscarded()
        when: "refreshes the keys"
        succeeds ":compileJava", "--refresh-keys"

        then:
        noExceptionThrown()

        where:
        terse << [true, false]
    }

    def "caches missing keys for 24h hours"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
        }

        given:
        javaLibrary()
        terseConsoleOutput(false)
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
        assertVerificationError(false) {
            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""
        }
        assertConfigCacheDiscarded()
        when: "publish keys"
        keyServerFixture.withDefaultSigningKey()
        fails ":compileJava", "-Dorg.gradle.internal.test.clockoffset=${TimeUnit.HOURS.toMillis(23)}"

        then:
        assertVerificationError(false) {
            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""
        }
        assertConfigCacheDiscarded()
        when: "24 hours passed"
        succeeds ":compileJava", "-Dorg.gradle.internal.test.clockoffset=${TimeUnit.HOURS.toMillis(24) + TimeUnit.MINUTES.toMillis(5)}"

        then:
        noExceptionThrown()
    }


    def "cache takes ignored keys into consideration"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
        }

        given:
        terseConsoleOutput(false)
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
        assertConfigCacheDiscarded()
        when: "ignore key"
        replaceMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addGloballyIgnoredKey("14f53f0824875d73")
        }
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) multiple problems reported:
      - in repository 'maven': artifact was signed but all keys were ignored
      - in repository 'maven': checksum is missing from verification metadata.
  - On artifact foo-1.0.pom (org:foo:1.0) multiple problems reported:
      - in repository 'maven': artifact was signed but all keys were ignored
      - in repository 'maven': checksum is missing from verification metadata."""
        assertConfigCacheDiscarded()
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
        assertConfigCacheDiscarded()
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
        assertConfigCacheDiscarded()
    }


    // This test exercises the fact that the signature cache is aware
    // of changes of the artifact
    def "can detect tampered file between builds (terse output=#terse)"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", SigningFixtures.validPublicKeyHexString, "pom", "pom")
        }
        keyServerFixture.withDefaultSigningKey()

        given:
        terseConsoleOutput(terse)
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
        def originHash = new File(version, "d48c8da6999eb2191744f01691f84675e7ff520b")
        def artifactFile = new File(originHash, "foo-1.0.jar")
        artifactFile.text = "tampered"
        fails ":compileJava"

        then:
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':compileClasspath'
One artifact failed verification: foo-1.0.jar (org:foo:1.0) from repository maven
This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""

            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key 'd7bf96a169f77b28c934ab1614f53f0824875d73' (Gradle Test (This is used for testing the gradle-signing-plugin) <test@gradle.org>) but signature didn't match"""
        }

        where:
        terse << [true, false]
    }

    def "caching takes trusted keys into account (terse output=#terse)"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
        }
        keyServerFixture.withDefaultSigningKey()

        given:
        terseConsoleOutput(terse)
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
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':compileClasspath'
One artifact failed verification: foo-1.0.jar (org:foo:1.0) from repository maven
If the artifacts are trustworthy, you will need to update the gradle/verification-metadata.xml file. ${docsUrl}"""

            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key 'd7bf96a169f77b28c934ab1614f53f0824875d73' (Gradle Test (This is used for testing the gradle-signing-plugin) <test@gradle.org>) and passed verification but the key isn't in your trusted keys list.

If the artifacts are trustworthy, you will need to update the gradle/verification-metadata.xml file. ${docsUrl}"""
        }

        where:
        terse << [true, false]
    }

    def "unsigned artifacts require checksum verification (terse output=#terse)"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addChecksum("org:foo:1.0", "sha256", "nope")
            addChecksum("org:foo:1.0", "sha256", "nope", "pom", "pom")
        }

        given:
        terseConsoleOutput(terse)
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
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':compileClasspath'
2 artifacts failed verification:
  - foo-1.0.jar (org:foo:1.0) from repository maven
  - foo-1.0.pom (org:foo:1.0) from repository maven
This can indicate that a dependency has been compromised. Please carefully verify the checksums."""

            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) multiple problems reported:
      - in repository 'maven': artifact is not signed
      - in repository 'maven': expected a 'sha256' checksum of 'nope' but was 'f46001e8577ce4fdaf4d1f9aed03311c581b08f9e82bf2406e70553101680212'
  - On artifact foo-1.0.pom (org:foo:1.0) multiple problems reported:
      - in repository 'maven': artifact is not signed
      - in repository 'maven': expected a 'sha256' checksum of 'nope' but was 'f331cce36f6ce9ea387a2c8719fabaf67dc5a5862227ebaa13368ff84eb69481'

This can indicate that a dependency has been compromised. Please carefully verify the checksums."""
        }
        assertConfigCacheDiscarded()
        where:
        terse << [true, false]
    }

    def "can ignore a key and fallback to checksum verification (terse output=#terse)"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addGloballyIgnoredKey(getValidPublicKeyLongIdHexString())
        }

        given:
        terseConsoleOutput(terse)
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
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':compileClasspath'
2 artifacts failed verification:
  - foo-1.0.jar (org:foo:1.0) from repository maven
  - foo-1.0.pom (org:foo:1.0) from repository maven"""

            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) multiple problems reported:
      - in repository 'maven': artifact was signed but all keys were ignored
      - in repository 'maven': checksum is missing from verification metadata.
  - On artifact foo-1.0.pom (org:foo:1.0) multiple problems reported:
      - in repository 'maven': artifact was signed but all keys were ignored
      - in repository 'maven': checksum is missing from verification metadata."""
        }
        assertConfigCacheDiscarded()
        where:
        terse << [true, false]
    }

    def "can ignore a key using full fingerprint and fallback to checksum verification (terse output=#terse)"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addGloballyIgnoredKey(SigningFixtures.validPublicKeyHexString)
        }

        given:
        terseConsoleOutput(terse)
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
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':compileClasspath'
2 artifacts failed verification:
  - foo-1.0.jar (org:foo:1.0) from repository maven
  - foo-1.0.pom (org:foo:1.0) from repository maven"""

            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) multiple problems reported:
      - in repository 'maven': artifact was signed but all keys were ignored
      - in repository 'maven': checksum is missing from verification metadata.
  - On artifact foo-1.0.pom (org:foo:1.0) multiple problems reported:
      - in repository 'maven': artifact was signed but all keys were ignored
      - in repository 'maven': checksum is missing from verification metadata."""
        }
        assertConfigCacheDiscarded()
        where:
        terse << [true, false]
    }

    def "can ignore a key for a specific artifact and fallback to checksum verification (terse output=#terse)"() {
        // we tamper the jar, so the verification of the jar would fail, but not the POM
        keyServerFixture.withDefaultSigningKey()
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addIgnoredKeyByFileName("org:foo:1.0", "foo-1.0.jar", validPublicKeyHexString)
        }

        given:
        terseConsoleOutput(terse)
        javaLibrary()
        def module = uncheckedModule("org", "foo", "1.0") {
            withSignature {
                signAsciiArmored(it)
            }
        }
        tamperWithFile(module.artifactFile)

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
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':compileClasspath'
2 artifacts failed verification:
  - foo-1.0.pom (org:foo:1.0) from repository maven
  - foo-1.0.jar (org:foo:1.0) from repository maven"""

            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key 'd7bf96a169f77b28c934ab1614f53f0824875d73' (Gradle Test (This is used for testing the gradle-signing-plugin) <test@gradle.org>) and passed verification but the key isn't in your trusted keys list.
  - On artifact foo-1.0.jar (org:foo:1.0) multiple problems reported:
      - in repository 'maven': artifact was signed but all keys were ignored
      - in repository 'maven': checksum is missing from verification metadata.

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""
        }
        assertConfigCacheDiscarded()
        where:
        terse << [true, false]

    }

    def "can ignore a key by long id for a specific artifact and fallback to checksum verification (terse output=#terse)"() {
        // we tamper the jar, so the verification of the jar would fail, but not the POM
        keyServerFixture.withDefaultSigningKey()
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addIgnoredKeyByFileName("org:foo:1.0", "foo-1.0.jar", validPublicKeyLongIdHexString)
        }

        given:
        terseConsoleOutput(terse)
        javaLibrary()
        def module = uncheckedModule("org", "foo", "1.0") {
            withSignature {
                signAsciiArmored(it)
            }
        }
        tamperWithFile(module.artifactFile)

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
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':compileClasspath'
2 artifacts failed verification:
  - foo-1.0.pom (org:foo:1.0) from repository maven
  - foo-1.0.jar (org:foo:1.0) from repository maven"""

            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key 'd7bf96a169f77b28c934ab1614f53f0824875d73' (Gradle Test (This is used for testing the gradle-signing-plugin) <test@gradle.org>) and passed verification but the key isn't in your trusted keys list.
  - On artifact foo-1.0.jar (org:foo:1.0) multiple problems reported:
      - in repository 'maven': artifact was signed but all keys were ignored
      - in repository 'maven': checksum is missing from verification metadata.

This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums."""
        }
        assertConfigCacheDiscarded()
        where:
        terse << [true, false]
    }

    def "fallbacks to checksum and fails if artifact has an entry in the verification-metadata but no checksum and .asc file when verify-signatures=#enableVerifySignatures"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            enableVerifySignatures ? verifySignatures() : {}
            // Add trusted keys just so there is an entry in the verification-metadata
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0")
        buildFile << """
            dependencies {
                implementation("org:foo:1.0")
            }
        """

        when:
        terseConsoleOutput(false)
        serveValidKey()

        then:
        fails ":compileJava"
        if (enableVerifySignatures) {
            failure.assertHasCause("""Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) multiple problems reported:
      - in repository 'maven': artifact is not signed
      - in repository 'maven': checksum is missing from verification metadata.
  - On artifact foo-1.0.pom (org:foo:1.0) multiple problems reported:
      - in repository 'maven': artifact is not signed
      - in repository 'maven': checksum is missing from verification metadata.""")
        } else {
            failure.assertHasCause("""Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': checksum is missing from verification metadata.
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': checksum is missing from verification metadata.""")
        }
        assertConfigCacheDiscarded()
        where:
        enableVerifySignatures << [true, false]
    }

    def "passes verification if an artifact is signed with multiple keys and one of them is ignored"() {
        def keyring = newKeyRing()
        keyServerFixture.registerPublicKey(keyring.publicKey)
        def pkId = toHexString(keyring.publicKey.fingerprint)
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

    def "can collect multiple errors for single dependency (terse output=#terse)"() {
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
        terseConsoleOutput(terse)
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
        assertVerificationError(terse) {
            whenTerse """Dependency verification failed for configuration ':compileClasspath'
2 artifacts failed verification:
  - foo-1.0.jar (org:foo:1.0) from repository maven
  - foo-1.0.pom (org:foo:1.0) from repository maven"""

            whenVerbose """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Multiple signature verification errors found:
      - Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
      - Artifact was signed with key '${pkId}' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Multiple signature verification errors found:
      - Artifact was signed with key '14f53f0824875d73' but it wasn't found in any key server so it couldn't be verified
      - Artifact was signed with key '${pkId}' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.

If the artifacts are trustworthy, you will need to update the gradle/verification-metadata.xml file. ${docsUrl}"""
        }
        assertConfigCacheDiscarded()
        where:
        terse << [true, false]
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

        then:
        succeeds ":compileJava"

    }

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
        terseConsoleOutput(false)
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
        if (!addLocalKey) {
            failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '${pkId}' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.
  - On artifact foo-1.0.pom (org:foo:1.0) in repository 'maven': Artifact was signed with key '${pkId}' (test-user@gradle.com) and passed verification but the key isn't in your trusted keys list.

If the artifacts are trustworthy, you will need to update the gradle/verification-metadata.xml file. ${docsUrl}"""
            assertConfigCacheDiscarded()
        }

        where:
        addLocalKey << [true, false]
    }

    def "can read public keys from #keyRingFormat keyring"() {
        // key will not be published on the server fixture but available locally
        def keyring = newKeyRing()
        def pkId = toHexString(keyring.publicKey.fingerprint)

        createMetadataFile {
            disableKeyServers()
            verifySignatures()
            addTrustedKey("org:foo:1.0", pkId)
            addTrustedKey("org:foo:1.0", pkId, "pom", "pom")
        }

        def verifFile = file("gradle/verification-keyring.${extension}")
        keyring.writePublicKeyRingTo(verifFile)
        if (header != null) {
            verifFile.setText("""$header
${verifFile.getText('us-ascii')}""", 'us-ascii')
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

        expect:
        succeeds ":compileJava"

        where:
        keyRingFormat       | extension | header
        'GPG'               | 'gpg'     | null
        'ASCII'             | 'keys'    | null
        'ASCII with header' | 'keys'    | 'some comment showing we can have arbitrary text'
    }

    @UnsupportedWithConfigurationCache
    def "can verify dependencies in a buildFinished hook (terse output=#terse)"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
        }

        given:
        terseConsoleOutput(terse)
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

            gradle.buildFinished {
               allprojects {
                  println configurations.compileClasspath.files
               }
            }
        """

        when:
        serveValidKey()
        fails ":help"

        then:
        failure.assertHasDescription terse ? """Dependency verification failed for configuration ':compileClasspath'
One artifact failed verification: foo-1.0.jar (org:foo:1.0) from repository maven""" : """Dependency verification failed for configuration ':compileClasspath':
  - On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key 'd7bf96a169f77b28c934ab1614f53f0824875d73' (Gradle Test (This is used for testing the gradle-signing-plugin) <test@gradle.org>) and passed verification but the key isn't in your trusted keys list.
"""
        where:
        terse << [true, false]
    }

    @ToBeFixedForConfigurationCache(because = "task uses Artifact Query API")
    def "dependency verification should work correctly with artifact queries"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "jar", "jar")
            addTrustedKeyByFileName("org:foo:1.0", "foo-1.0-javadoc.jar", validPublicKeyHexString)
            addTrustedKeyByFileName("org:foo:1.0", "foo-1.0-sources.jar", validPublicKeyHexString)
        }

        terseConsoleOutput(false)
        javaLibrary()
        def module = mavenHttpRepo.module("org", "foo", "1.0")
            .withSourceAndJavadoc()
            .withSignature {
                signAsciiArmored(it)
            }
            .publish()
        serveValidKey()
        buildFile << """
            dependencies {
                implementation "org:foo:1.0"
            }

            tasks.register("artifactQuery") {
                doLast {
                    dependencies.createArtifactResolutionQuery()
                        .forModule("org", "foo", "1.0")
                        .withArtifacts(JvmLibrary, SourcesArtifact, JavadocArtifact)
                        .execute()
                        .components
                        .each {
                            // trigger file access for verification to happen
                            it.getArtifacts(SourcesArtifact)*.file
                            it.getArtifacts(JavadocArtifact)*.file
                        }
                }
            }
        """

        when:
        module.pom.expectGet()
        module.getArtifact(type: 'pom.asc').expectGet()
        module.getArtifact(classifier: 'sources').expectHead()
        module.getArtifact(classifier: 'sources').expectGet()
        module.getArtifact(classifier: 'sources', type: 'jar.asc').expectGet()
        module.getArtifact(classifier: 'javadoc').expectHead()
        module.getArtifact(classifier: 'javadoc').expectGet()
        module.getArtifact(classifier: 'javadoc', type: 'jar.asc').expectGet()
        run ":artifactQuery"

        then:
        noExceptionThrown()
    }

    def "can disable reaching out to key servers"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri) // make sure we declare a key server for tests
            disableKeyServers() // but disable access to the key server
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
        }
        serveValidKey()

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
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath'
2 artifacts failed verification:
  - foo-1.0.jar (org:foo:1.0) from repository maven
  - foo-1.0.pom (org:foo:1.0) from repository maven
This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums. Key servers are disabled, this can indicate that you need to update the local keyring with the missing keys."""
        assertConfigCacheDiscarded()
    }

    @Issue("https://github.com/gradle/gradle/issues/19663")
    def "fails when disabling reaching out to key servers after previous successful build and no key rings file"() {
        given:
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
        }
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
        serveValidKey()

        when:
        succeeds ":compileJava"

        then:
        noExceptionThrown()

        when:
        replaceMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
            disableKeyServers()
        }
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath'
2 artifacts failed verification:
  - foo-1.0.jar (org:foo:1.0) from repository maven
  - foo-1.0.pom (org:foo:1.0) from repository maven
This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums. Key servers are disabled, this can indicate that you need to update the local keyring with the missing keys."""
    }

    @Issue("https://github.com/gradle/gradle/issues/18440")
    def "fails on a bad verification file change after previous successful build when key servers are disabled"() {
        def keyring = newKeyRing()
        def pkId = toHexString(keyring.publicKey.fingerprint)

        createMetadataFile {
            disableKeyServers()
            verifySignatures()
            addTrustedKey("org:foo:1.0", pkId)
            addTrustedKey("org:foo:1.0", pkId, "pom", "pom")
        }

        def verificationFile = file("gradle/verification-keyring.keys")
        keyring.writePublicKeyRingTo(verificationFile)

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
        noExceptionThrown()

        when:
        verificationFile.delete()
        fails ":compileJava"

        then:
        failure.assertHasCause """Dependency verification failed for configuration ':compileClasspath'
2 artifacts failed verification:
  - foo-1.0.jar (org:foo:1.0) from repository maven
  - foo-1.0.pom (org:foo:1.0) from repository maven
This can indicate that a dependency has been compromised. Please carefully verify the signatures and checksums. Key servers are disabled, this can indicate that you need to update the local keyring with the missing keys."""
    }

    @Issue("https://github.com/gradle/gradle/issues/20098")
    def "doesn't fail for a variant that has a file name in the Gradle Module Metadata different to actual artifact"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "module", "module")
            addTrustedKeyByFileName("org:foo:1.0", "foo.klib", validPublicKeyHexString)
        }

        given:
        javaLibrary()
        uncheckedModule("org", "foo", "1.0") {
            withSignature {
                signAsciiArmored(it)
            }
            withoutDefaultVariants()
            withVariant("linux64") {
                attribute(Usage.USAGE_ATTRIBUTE.name, "linux64")
                attribute(Category.CATEGORY_ATTRIBUTE.name, Category.LIBRARY)
                useDefaultArtifacts = false
                artifact("foo.klib", "foo-linux64-1.0.klib")
            }
        }.withModuleMetadata().publish()
        buildFile << """
            dependencies {
                implementation("org:foo:1.0") {
                    attributes {
                        attribute(Attribute.of(Usage.USAGE_ATTRIBUTE.name, String), "linux64")
                    }
                }
            }
        """

        when:
        serveValidKey()

        then:
        succeeds ":compileJava"
    }

    def "fails verification if a per artifact trusted key is not a fingerprint"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addTrustedKey("org:foo:1.0", validPublicKeyHexString)
            addTrustedKey("org:foo:1.0", validPublicKeyHexString, "pom", "pom")
        }

        // We need to manually replace the key in the XML, as 'createMetadataFile' will already fail if we use a non-fingerprint ID
        def longId = validPublicKeyHexString.substring(validPublicKeyHexString.length() - 16)
        file("gradle/verification-metadata.xml").replace(validPublicKeyHexString, longId)

        given:
        terseConsoleOutput(terse)
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
        assertConfigCacheDiscarded()
        failureCauseContains("An error happened meanwhile verifying 'org:foo:1.0'")
        failureCauseContains("The following trusted GPG IDs are not in a minimum 160-bit fingerprint format")
        failureCauseContains("'${longId}'")

        where:
        terse << [true, false]
    }

    def "fails verification if a globally trusted key is not a fingerprint"() {
        createMetadataFile {
            keyServer(keyServerFixture.uri)
            verifySignatures()
            addGloballyTrustedKey(validPublicKeyHexString, "org", "foo", "1.0", "foo-1.0-classified.jar", false)
        }

        // We need to manually replace the key in the XML, as 'createMetadataFile' will already fail if we use a non-fingerprint ID
        def longId = validPublicKeyHexString.substring(validPublicKeyHexString.length() - 16)
        file("gradle/verification-metadata.xml").replace(validPublicKeyHexString, longId)

        given:
        terseConsoleOutput(terse)
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
        assertConfigCacheDiscarded()
        failureCauseContains("The following trusted GPG IDs are not in a minimum 160-bit fingerprint format")
        failureCauseContains("'${longId}'")

        where:
        terse << [true, false]
    }

    private static void tamperWithFile(File file) {
        file.bytes = [0, 1, 2, 3] + file.readBytes().toList() as byte[]
    }
}
