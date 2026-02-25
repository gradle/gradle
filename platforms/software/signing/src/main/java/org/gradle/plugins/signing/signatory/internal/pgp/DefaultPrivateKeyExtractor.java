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

package org.gradle.plugins.signing.signatory.internal.pgp;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.concurrent.atomic.AtomicReference;

public class DefaultPrivateKeyExtractor implements PrivateKeyExtractor {

    private static class CachedPrivateKey {
        private final PGPPrivateKey privateKey;
        private final PGPSecretKey secretKey;
        private final int passwordMarker;

        private CachedPrivateKey(PGPSecretKey secretKey, PGPPrivateKey privateKey, String password) {
            this.secretKey = secretKey;
            this.privateKey = privateKey;
            this.passwordMarker = password.hashCode();
        }

        boolean isFor(PGPSecretKey secretKey, String password) {
            // Compare by key ID and a derived value of the password (hashCode) to determine if it's the same underlying key/decryption
            return this.secretKey.getKeyID() == secretKey.getKeyID() && this.passwordMarker == password.hashCode();
        }
    }

    private final AtomicReference<CachedPrivateKey> cachedPrivateKey = new AtomicReference<>();

    @Override
    public PGPPrivateKey extractPrivateKey(PGPSecretKey secretKey, String password) {
        CachedPrivateKey updated = cachedPrivateKey.updateAndGet(current -> {
            if (current != null && current.isFor(secretKey, password)) {
                return current;
            }
            PGPPrivateKey loaded = doExtract(secretKey, password);
            return new CachedPrivateKey(secretKey, loaded, password);
        });
        return updated.privateKey;
    }

    static PGPPrivateKey doExtract(PGPSecretKey secretKey, String password) {
        try {
            PBESecretKeyDecryptor decryptor = new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider()).build(password.toCharArray());
            return secretKey.extractPrivateKey(decryptor);
        } catch (PGPException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
