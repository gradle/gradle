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

package org.gradle.plugins.signing.signatory.pgp;

import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.ServiceReference;
import org.gradle.plugins.signing.signatory.internal.pgp.PgpSignatoryService;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * This is an implementation of PgpSignatory that uses {@link PgpSignatoryService} to cache computed keys.
 */
class PgpSignatoryInternal extends PgpSignatory {
    private final Provider<PgpSignatoryService> signatoryService;

    public PgpSignatoryInternal(Provider<PgpSignatoryService> signatoryService, String name, Provider<PGPSecretKey> secretKey, Provider<PGPPrivateKey> privateKey) {
        super(name, secretKey, privateKey);
        this.signatoryService = signatoryService;
    }

    /**
     * A reference to the signatory service to adhere to the contract of {@link org.gradle.api.Task#usesService(Provider)}.
     * The service is used as a base of secretKey and privateKey providers.
     */
    @ServiceReference
    public Provider<PgpSignatoryService> getSignatoryService() {
        // TODO(mlopatkin): we cannot get an auto-resolving @ServiceReference property without also being ExtensionAware.
        //  Using @NonExtensible also breaks property injection. ExtensionAware is a contract we don't want for PgpSignatory.
        return signatoryService;
    }

    @Override
    public void sign(InputStream toSign, OutputStream signatureDestination) {
        super.sign(toSign, signatureDestination);
    }
}
