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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.writer

import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters
import org.bouncycastle.openpgp.PGPKeyRingGenerator
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.bouncycastle.openpgp.operator.bc.BcPGPKeyPair
import org.gradle.security.internal.Fingerprint
import org.gradle.security.internal.SecuritySupport
import spock.lang.Specification

import java.security.SecureRandom

import static java.util.Collections.emptySet
import static org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.writer.WriteDependencyVerificationFile.isReferenced

class WriteDependencyVerificationFileIsReferencedTest extends Specification {

    // Generate a fresh keyring with master + encryption subkey, the common shape of keys this
    // code path is meant to handle (whole-ring preservation when a subkey is referenced).
    PGPPublicKeyRing ring = generateRingWithSubkey()

    PGPPublicKey master = ring.publicKeys.findAll { it.masterKey }.first()
    PGPPublicKey subkey = ring.publicKeys.findAll { !it.masterKey }.first()

    def "empty reference set matches nothing"() {
        expect:
        !isReferenced(ring, emptySet())
    }

    def "matches on master long id"() {
        expect:
        isReferenced(ring, [SecuritySupport.toLongIdHexString(master.keyID).toUpperCase(Locale.ROOT)] as Set)
    }

    def "matches on master fingerprint"() {
        expect:
        isReferenced(ring, [Fingerprint.of(master).toString()] as Set)
    }

    def "matches on subkey long id — preserves the whole ring"() {
        expect:
        isReferenced(ring, [SecuritySupport.toLongIdHexString(subkey.keyID).toUpperCase(Locale.ROOT)] as Set)
    }

    def "matches on subkey fingerprint — preserves the whole ring"() {
        expect:
        isReferenced(ring, [Fingerprint.of(subkey).toString()] as Set)
    }

    def "does not match on unrelated key id"() {
        expect:
        !isReferenced(ring, ['DEADBEEFDEADBEEF'] as Set)
    }

    def "is case-sensitive — lower-case key id does not match upper-case reference"() {
        // Callers are expected to uppercase their hex, per the contract at the call sites.
        given:
        def lower = SecuritySupport.toLongIdHexString(master.keyID).toLowerCase(Locale.ROOT)
        expect:
        !isReferenced(ring, [lower] as Set)
    }

    private static PGPPublicKeyRing generateRingWithSubkey() {
        def date = new Date()
        def rsa = new RSAKeyPairGenerator()
        rsa.init(new RSAKeyGenerationParameters(0x10001G, new SecureRandom(), 1024, 12))
        def signingKeyPair = new BcPGPKeyPair(PGPPublicKey.RSA_SIGN, rsa.generateKeyPair(), date)
        def encryptionKeyPair = new BcPGPKeyPair(PGPPublicKey.RSA_ENCRYPT, rsa.generateKeyPair(), date)

        def signingSubpackets = new PGPSignatureSubpacketGenerator()
        signingSubpackets.setKeyFlags(false, KeyFlags.SIGN_DATA | KeyFlags.CERTIFY_OTHER)

        def encryptionSubpackets = new PGPSignatureSubpacketGenerator()
        encryptionSubpackets.setKeyFlags(false, KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE)

        def generator = new PGPKeyRingGenerator(
            PGPPublicKey.RSA_SIGN,
            signingKeyPair,
            "test-user@gradle.com",
            new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1),
            signingSubpackets.generate(),
            null,
            new BcPGPContentSignerBuilder(PGPPublicKey.RSA_SIGN, HashAlgorithmTags.SHA512),
            new BcPBESecretKeyEncryptorBuilder(PublicKeyAlgorithmTags.RSA_GENERAL).build("secret".toCharArray())
        )
        generator.addSubKey(encryptionKeyPair, encryptionSubpackets.generate(), null)
        generator.generatePublicKeyRing()
    }
}
