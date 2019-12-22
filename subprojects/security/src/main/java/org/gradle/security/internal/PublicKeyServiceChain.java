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
import java.util.Optional;

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
    public Optional<PGPPublicKey> findPublicKey(long id) {
        for (PublicKeyService service : services) {
            Optional<PGPPublicKey> publicKey = service.findPublicKey(id);
            if (publicKey.isPresent()) {
                return publicKey;
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<PGPPublicKeyRing> findKeyRing(long id) {
        for (PublicKeyService service : services) {
            Optional<PGPPublicKeyRing> publicKeyRing = service.findKeyRing(id);
            if (publicKeyRing.isPresent()) {
                return publicKeyRing;
            }
        }
        return Optional.empty();
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
}
