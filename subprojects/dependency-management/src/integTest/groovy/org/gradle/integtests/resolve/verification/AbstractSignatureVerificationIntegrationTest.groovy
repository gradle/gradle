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

import org.gradle.integtests.fixtures.cache.CachingIntegrationFixture
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.security.fixtures.KeyServer
import org.gradle.security.fixtures.SigningFixtures
import org.gradle.security.fixtures.SimpleKeyRing
import org.gradle.security.internal.Fingerprint

import static org.gradle.security.fixtures.SigningFixtures.createSimpleKeyRing

abstract class AbstractSignatureVerificationIntegrationTest extends AbstractDependencyVerificationIntegTest implements CachingIntegrationFixture {
    KeyServer keyServerFixture

    def setup() {
        keyServerFixture = new KeyServer(temporaryFolder.createDir("keyserver"))
        keyServerFixture.start()
    }

    @Override
    def cleanup() {
        keyServerFixture.stop()
    }

    protected void serveMissingKey(String keyId = SigningFixtures.validPublicKeyHexString) {
        keyServerFixture.missingKey(keyId)
    }

    protected void serveValidKey() {
        keyServerFixture.withDefaultSigningKey()
    }

    protected SimpleKeyRing newKeyRing() {
        def ring = createKeyRing()
        def minId = new BigInteger(SigningFixtures.validPublicKeyHexString, 16)
        // This loop is just to avoid some flakiness in tests which
        // expect error messages in a certain order
        while (new BigInteger(Fingerprint.of(ring.publicKey).toString(), 16) < minId) {
            ring = createKeyRing()
        }
        ring
    }

    private SimpleKeyRing createKeyRing() {
        createSimpleKeyRing(temporaryFolder.createDir("keys-${UUID.randomUUID()}"))
    }

    protected GradleExecuter writeVerificationMetadata(String checksums = "sha256,pgp") {
        executer.withArguments("--write-verification-metadata", checksums)
    }
}
