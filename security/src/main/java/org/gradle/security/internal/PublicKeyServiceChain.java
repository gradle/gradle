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
package org.gradle.security.internal;

import com.google.common.collect.ImmutableList;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.util.List;

public class PublicKeyServiceChain implements PublicKeyService {
    private final static Logger LOGGER = Logging.getLogger(PublicKeyServiceChain.class);

    private final List<PublicKeyService> services;

    public static PublicKeyService of(PublicKeyService... delegates) {
        return new PublicKeyServiceChain(ImmutableList.copyOf(delegates));
    }

    private PublicKeyServiceChain(List<PublicKeyService> services) {
        this.services = services;
    }

    @Override
    public void findByLongId(long keyId, PublicKeyResultBuilder builder) {
        FirstMatchBuilder fmb = new FirstMatchBuilder(builder);
        for (PublicKeyService service : services) {
            service.findByLongId(keyId, fmb);
            if (fmb.hasResult) {
                return;
            }
        }
    }

    @Override
    public void findByFingerprint(byte[] fingerprint, PublicKeyResultBuilder builder) {
        FirstMatchBuilder fmb = new FirstMatchBuilder(builder);
        for (PublicKeyService service : services) {
            service.findByFingerprint(fingerprint, fmb);
            if (fmb.hasResult) {
                return;
            }
        }
    }

    @Override
    public void close() {
        for (PublicKeyService service : services) {
            try {
                service.close();
            } catch (Exception e) {
                LOGGER.warn("Cannot close service", e);
            }
        }
    }

    private static class FirstMatchBuilder implements PublicKeyResultBuilder {
        private final PublicKeyResultBuilder delegate;
        public boolean hasResult;

        private FirstMatchBuilder(PublicKeyResultBuilder delegate) {
            this.delegate = delegate;
        }

        @Override
        public void keyRing(PGPPublicKeyRing keyring) {
            delegate.keyRing(keyring);
            hasResult = true;
        }

        @Override
        public void publicKey(PGPPublicKey publicKey) {
            delegate.publicKey(publicKey);
            hasResult = true;
        }
    }
}
