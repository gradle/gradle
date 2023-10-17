/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.artifacts.verification.signatures;

import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerificationConfiguration;
import org.gradle.security.internal.KeyringFilePublicKeyService;
import org.gradle.security.internal.PublicKeyService;
import org.gradle.security.internal.PublicKeyServiceChain;
import org.gradle.security.internal.SecuritySupport;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class BuildTreeDefinedKeys {
    private static final String VERIFICATION_KEYRING_GPG = "verification-keyring.gpg";
    private static final String VERIFICATION_KEYRING_ASCII = "verification-keyring.keys";

    private final KeyringFilePublicKeyService keyService;
    private final File keyringsRoot;

    private final File effectiveKeyringsFile;

    public BuildTreeDefinedKeys(
        File keyringsRoot,
        @Nullable DependencyVerificationConfiguration.KeyringFormat effectiveFormat
    ) {
        this.keyringsRoot = keyringsRoot;

        File effectiveFile;
        if (effectiveFormat == DependencyVerificationConfiguration.KeyringFormat.ARMORED) {
            effectiveFile = getAsciiKeyringsFile();
        } else if (effectiveFormat == DependencyVerificationConfiguration.KeyringFormat.BINARY) {
            effectiveFile = getBinaryKeyringsFile();
        } else if (effectiveFormat == null) {
            effectiveFile = getBinaryKeyringsFile();
            if (!effectiveFile.exists()) {
                effectiveFile = getAsciiKeyringsFile();
            }
        } else {
            throw new IllegalArgumentException("Unknown keyring format: " + effectiveFormat);
        }

        if (effectiveFile.exists()) {
            this.effectiveKeyringsFile = effectiveFile;
            this.keyService = new KeyringFilePublicKeyService(effectiveKeyringsFile);
        } else {
            this.effectiveKeyringsFile = null;
            this.keyService = null;
        }
    }

    public File getBinaryKeyringsFile() {
        return new File(keyringsRoot, VERIFICATION_KEYRING_GPG);
    }

    public File getAsciiKeyringsFile() {
        return new File(keyringsRoot, VERIFICATION_KEYRING_ASCII);
    }

    public File getEffectiveKeyringsFile() {
        return effectiveKeyringsFile;
    }

    public List<PGPPublicKeyRing> loadKeys() throws IOException {
        if (effectiveKeyringsFile != null) {
            return SecuritySupport.loadKeyRingFile(effectiveKeyringsFile);
        } else {
            return Collections.emptyList();
        }
    }

    public PublicKeyService applyTo(PublicKeyService original) {
        if (keyService != null) {
            return PublicKeyServiceChain.of(keyService, original);
        } else {
            return original;
        }
    }
}
