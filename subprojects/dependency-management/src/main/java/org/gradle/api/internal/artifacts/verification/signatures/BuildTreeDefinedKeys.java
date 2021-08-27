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
import org.gradle.security.internal.KeyringFilePublicKeyService;
import org.gradle.security.internal.PublicKeyService;
import org.gradle.security.internal.PublicKeyServiceChain;
import org.gradle.security.internal.SecuritySupport;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride.VERIFICATION_KEYRING_DRYRUN_GPG;

public class BuildTreeDefinedKeys {
    private final KeyringFilePublicKeyService keyService;
    private final File keyringsFile;
    private final File effectiveKeyringsFile;

    public BuildTreeDefinedKeys(File keyringsFile) {
        this.keyringsFile = keyringsFile;
        if (!keyringsFile.exists()) {
            keyringsFile = getAsciiKeyringsFile();
        }
        if (keyringsFile.exists()) {
            this.effectiveKeyringsFile = keyringsFile;
            keyService = new KeyringFilePublicKeyService(keyringsFile);
        } else {
            this.effectiveKeyringsFile = null;
            keyService = null;
        }
    }

    public File getBinaryKeyringsFile() {
        return keyringsFile;
    }

    public File getAsciiKeyringsFile() {
        return SecuritySupport.asciiArmoredFileFor(keyringsFile);
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

    public BuildTreeDefinedKeys dryRun() {
        return new BuildTreeDefinedKeys(new File(keyringsFile.getParentFile(), VERIFICATION_KEYRING_DRYRUN_GPG));
    }
}
