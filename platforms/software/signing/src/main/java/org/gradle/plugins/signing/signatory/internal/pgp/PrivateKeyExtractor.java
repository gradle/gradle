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

import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * A service that extracts a private key from a secret key.
 */
@ServiceScope(Scope.Build.class)
public interface PrivateKeyExtractor {
    /**
     * Extracts a private key from the given secret yet. Extraction may be expensive, so this service may cache
     * secret/private key pairs.
     *
     * @param secretKey the secret key to extract the private key from
     * @param password the password to use
     * @return the private key extracted
     */
    PGPPrivateKey extractPrivateKey(PGPSecretKey secretKey, String password);

    /**
     * A plain-implementation of this service. Never caches.
     */
    PrivateKeyExtractor DEFAULT = DefaultPrivateKeyExtractor::doExtract;
}
