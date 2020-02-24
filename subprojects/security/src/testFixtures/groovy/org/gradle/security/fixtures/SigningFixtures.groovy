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
package org.gradle.security.fixtures

import groovy.transform.CompileStatic
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.CompressionAlgorithmTags
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters
import org.bouncycastle.openpgp.PGPKeyRingGenerator
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator
import org.bouncycastle.openpgp.bc.BcPGPPublicKeyRingCollection
import org.bouncycastle.openpgp.bc.BcPGPSecretKeyRingCollection
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.bouncycastle.openpgp.operator.bc.BcPGPKeyPair
import org.gradle.plugins.signing.signatory.pgp.PgpSignatory
import org.gradle.plugins.signing.signatory.pgp.PgpSignatoryFactory
import org.gradle.plugins.signing.type.pgp.ArmoredSignatureType
import org.gradle.security.internal.SecuritySupport

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom

@CompileStatic
class SigningFixtures {

    static PgpSignatory getValidSignatory() {
        Holder.VALID_KEYRING.signatory
    }

    static PGPPublicKey getValidPublicKey() {
        Holder.VALID_KEYRING.publicKey
    }

    static PGPSecretKey getValidSecretKey() {
        Holder.VALID_KEYRING.secretKey
    }

    static String getValidPassword() {
        Holder.VALID_KEYRING.password
    }

    static String getValidPublicKeyLongIdHexString() {
        SecuritySupport.toLongIdHexString(Holder.VALID_KEYRING.publicKey.keyID)
    }

    static String getValidPublicKeyHexString() {
        SecuritySupport.toHexString(Holder.VALID_KEYRING.publicKey.fingerprint)
    }

    static File signAsciiArmored(File toSign) {
        def armored = new ArmoredSignatureType()
        return armored.sign(validSignatory, toSign)
    }

    static PGPSecretKey readSecretKey(File keyringsDir) {
        def keyring = new File(keyringsDir, "secring.gpg")
        PGPSecretKey secretKey = null
        keyring.withInputStream {
            new PGPSecretKeyRingCollection(it, new BcKeyFingerprintCalculator()).each {
                secretKey = it.secretKey
            }
        }
        return secretKey
    }

    static PGPPublicKey readPublicKey(File keyringsDir) {
        def keyring = new File(keyringsDir, "pubring.gpg")
        PGPPublicKey publicKey = null
        keyring.withInputStream {
            new PGPPublicKeyRingCollection(it, new BcKeyFingerprintCalculator()).each {
                publicKey = it.publicKey
            }
        }
        return publicKey
    }

    static void writeValidPublicKeyTo(File file) {
        file.newOutputStream().withCloseable { stream ->
            new ArmoredOutputStream(stream).withCloseable {
                Holder.VALID_KEYRING.publicKey.encode(it)
            }
        }
    }

    static SimpleKeyRing createSimpleKeyRing(File directory, String name = "gradle", String userId = "test-user@gradle.com", String password = "secret", int keySize = 1024) {
        def keyringDir = createKeyRingsInto(directory, userId, password, keySize)
        def secretKey = readSecretKey(keyringDir)
        def publicKey = readPublicKey(keyringDir)
        new SimpleKeyRing(name, secretKey, publicKey, password)
    }

    static File createKeyRingsInto(File directory, String userId = "test-user@gradle.com", String password = "secret", int keySize = 1024) {
        File secring = new File(directory, "secring.gpg")
        File pubring = new File(directory, "pubring.gpg")
        def generator = createKeyRingGenerator(userId, password, keySize)
        def secretKeys = generator.generateSecretKeyRing().secretKeys.collect { it }
        PGPSecretKeyRing secret = new PGPSecretKeyRing(secretKeys)
        PGPSecretKeyRingCollection ring = new BcPGPSecretKeyRingCollection(
            [secret]
        )
        new FileOutputStream(secring).withCloseable {
            ring.encode(it)
        }
        def publicKeys = generator.generatePublicKeyRing().publicKeys.collect { it }
        PGPPublicKeyRing pub = new PGPPublicKeyRing(publicKeys)
        PGPPublicKeyRingCollection pubcol = new BcPGPPublicKeyRingCollection(
            [pub]
        )
        new FileOutputStream(pubring).withCloseable {
            pubcol.encode(it)
        }
        directory
    }


    static PGPKeyRingGenerator createKeyRingGenerator(String userId = "test-user@gradle.com", String password = "secret", int keySize = 1024) {
        def date = new Date()
        def rSAKeyPairGenerator = new RSAKeyPairGenerator()
        rSAKeyPairGenerator.init(new RSAKeyGenerationParameters(0x10001G, new SecureRandom(), keySize, 12))
        def signingKeyPair = new BcPGPKeyPair(PGPPublicKey.RSA_SIGN, rSAKeyPairGenerator.generateKeyPair(), date)
        def encryptionKeyPair = new BcPGPKeyPair(PGPPublicKey.RSA_ENCRYPT, rSAKeyPairGenerator.generateKeyPair(), date)
        def signatureSubpacketGenerator = new PGPSignatureSubpacketGenerator()
        signatureSubpacketGenerator.setKeyFlags(false, KeyFlags.SIGN_DATA | KeyFlags.CERTIFY_OTHER);
        signatureSubpacketGenerator.setPreferredSymmetricAlgorithms(false, PublicKeyAlgorithmTags.DSA)
        signatureSubpacketGenerator.setPreferredHashAlgorithms(false, HashAlgorithmTags.SHA512)
        signatureSubpacketGenerator.setPreferredCompressionAlgorithms(false, CompressionAlgorithmTags.ZIP)

        def encryptionSubpacketGenerator = new PGPSignatureSubpacketGenerator()
        encryptionSubpacketGenerator.setKeyFlags(false, KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE)

        def generator = new PGPKeyRingGenerator(
            PGPPublicKey.RSA_SIGN,
            signingKeyPair,
            userId,
            new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1),
            signatureSubpacketGenerator.generate(),
            null,
            new BcPGPContentSignerBuilder(PGPPublicKey.RSA_SIGN, HashAlgorithmTags.SHA512),
            new BcPBESecretKeyEncryptorBuilder(PublicKeyAlgorithmTags.RSA_GENERAL).build(password.toCharArray()))
        generator.addSubKey(encryptionKeyPair, encryptionSubpacketGenerator.generate(), null)
        generator
    }

    private static class Holder {
        static final KeyRing VALID_KEYRING = new KeyRing("gradle")
        static final KeyRing INVALID_KEYRING = new KeyRing("invalid-key-ring")
    }

    private static class KeyRing {
        private final PgpSignatory signatory
        private final PGPPublicKey publicKey
        private final PGPSecretKey secretKey
        private final String password
        private final File keyRingFile

        KeyRing(String name) {
            def factory = new PgpSignatoryFactory()
            def keyId = new String(SigningFixtures.getResourceAsStream("/keys/$name/keyId.txt").bytes).trim()
            password = new String(SigningFixtures.getResourceAsStream("/keys/$name/password.txt").bytes).trim()
            keyRingFile = createKeyRingFile(name)
            PgpSignatory si = null
            PGPPublicKey pk = null
            PGPSecretKey sk = null
            try {
                si = factory.createSignatory(name, keyId, keyRingFile, password)
                sk = factory.readSecretKey(keyId, keyRingFile)
                pk = sk.publicKey
            } catch (Exception ex) {
                // invalid keyring
            } finally {
                signatory = si
                secretKey = sk
                publicKey = pk
            }
        }

        private static File createKeyRingFile(String name) {
            def tmpKeyRingFile = Files.createTempFile("keyring", ".tmp")
            Files.copy(
                SigningFixtures.getResourceAsStream("/keys/$name/secring.gpg"),
                tmpKeyRingFile,
                StandardCopyOption.REPLACE_EXISTING
            )
            File result = tmpKeyRingFile.toFile()
            result.deleteOnExit()
            result
        }
    }
}
