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

package org.gradle.vcs.git.internal

import org.apache.sshd.common.NamedResource
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.PublicKeyEntry
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.common.signature.BuiltinSignatures
import org.apache.sshd.common.util.security.SecurityUtils
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * JGit's SSH transport delegates key handling to Apache SSHD. Gradle does not ship net.i2p.crypto:eddsa,
 * so SSHD must fall back to BouncyCastle for Ed25519, as Java versions before 15 have no EdDSA support.
 */
class Ed25519SshKeySupportTest extends Specification {

    // Throwaway key generated for this test with `ssh-keygen -t ed25519 -N ''`
    private static final String PRIVATE_KEY = """-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
QyNTUxOQAAACCV5rRju4/Wdd4zpfRbtG28QtS9CmHB/3Vcl4iKeu8lkgAAAJjHGA8oxxgP
KAAAAAtzc2gtZWQyNTUxOQAAACCV5rRju4/Wdd4zpfRbtG28QtS9CmHB/3Vcl4iKeu8lkg
AAAEDNyBF13aOhvNLybDgA2pBybJB9L/qBR3Sg1C00Bf6lM5XmtGO7j9Z13jOl9Fu0bbxC
1L0KYcH/dVyXiIp67yWSAAAAD2dyYWRsZS10ZXN0LWtleQECAwQFBg==
-----END OPENSSH PRIVATE KEY-----
"""
    private static final String PUBLIC_KEY = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIJXmtGO7j9Z13jOl9Fu0bbxC1L0KYcH/dVyXiIp67yWS gradle-test-key"

    def "net.i2p.crypto:eddsa is not on the classpath"() {
        when:
        Class.forName("net.i2p.crypto.eddsa.EdDSAEngine")

        then:
        thrown(ClassNotFoundException)
        !SecurityUtils.isNetI2pCryptoEdDSARegistered()
    }

    def "SSHD supports Ed25519 via BouncyCastle"() {
        expect:
        SecurityUtils.isBouncyCastleRegistered()
        SecurityUtils.isEDDSACurveSupported()
        SecurityUtils.getEdDSASupport().get().class.name.contains("BouncyCastle")
    }

    def "can load an OpenSSH Ed25519 private key and sign with it"() {
        given:
        def keyPair = loadPrivateKey()
        def publicKey = PublicKeyEntry.parsePublicKeyEntry(PUBLIC_KEY).resolvePublicKey(null, [:], PublicKeyEntryResolver.FAILING)
        def data = "data to sign".getBytes(StandardCharsets.UTF_8)

        expect:
        KeyUtils.getKeyType(keyPair) == KeyPairProvider.SSH_ED25519
        KeyUtils.compareKeys(keyPair.public, publicKey)

        when:
        def signer = BuiltinSignatures.ed25519.create()
        signer.initSigner(null, keyPair.private)
        signer.update(null, data)
        def signature = signer.sign(null)

        def verifier = BuiltinSignatures.ed25519.create()
        verifier.initVerifier(null, publicKey)
        verifier.update(null, data)

        then:
        verifier.verify(null, signature)
    }

    private static loadPrivateKey() {
        def keyPairs = SecurityUtils.loadKeyPairIdentities(null, NamedResource.ofName("id_ed25519"), new ByteArrayInputStream(PRIVATE_KEY.getBytes(StandardCharsets.US_ASCII)), null)
        return keyPairs.iterator().next()
    }
}
