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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.writer;

import org.bouncycastle.openpgp.PGPPublicKey;
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationResultBuilder;
import org.gradle.security.internal.Fingerprint;

import java.util.Set;

class WriterSignatureVerificationResult implements SignatureVerificationResultBuilder {
    private final Set<String> ignoredKeys;
    private final PgpEntry entry;

    public WriterSignatureVerificationResult(Set<String> ignoredKeys, PgpEntry entry) {
        this.ignoredKeys = ignoredKeys;
        this.entry = entry;
    }

    @Override
    public void missingKey(String keyId) {
        ignoredKeys.add(keyId);
        entry.missing();
    }

    @Override
    public void verified(PGPPublicKey key, boolean trusted) {
        String keyId = Fingerprint.of(key).toString();
        entry.addVerifiedKey(keyId);
    }

    @Override
    public void failed(PGPPublicKey key) {
        String keyId = Fingerprint.of(key).toString();
        entry.fail(keyId);
    }

    @Override
    public void ignored(String keyId) {
        ignoredKeys.add(keyId);
    }
}
