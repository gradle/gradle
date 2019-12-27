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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class KeyringFilePublicKeyService implements PublicKeyService {
    private final static Logger LOGGER = Logging.getLogger(KeyringFilePublicKeyService.class);

    private final Map<Long, PGPPublicKeyRing> keyToKeyring;
    private final Map<Long, PGPPublicKey> keyToPublicKey;

    public KeyringFilePublicKeyService(File keyRingFile) {
        try {
            List<PGPPublicKeyRing> keyrings = SecuritySupport.loadKeyRingFile(keyRingFile);
            Map<Long, PGPPublicKeyRing> keyToKeyringBuilder = Maps.newHashMap();
            Map<Long, PGPPublicKey> keyToPublicKeyBuilder = Maps.newHashMap();
            for (PGPPublicKeyRing keyring : keyrings) {
                Iterator<PGPPublicKey> it = keyring.getPublicKeys();
                while (it.hasNext()) {
                    PGPPublicKey key = it.next();
                    long keyID = key.getKeyID();
                    keyToKeyringBuilder.put(keyID, keyring);
                    keyToPublicKeyBuilder.put(keyID, key);
                }
            }
            keyToKeyring = ImmutableMap.copyOf(keyToKeyringBuilder);
            keyToPublicKey = ImmutableMap.copyOf(keyToPublicKeyBuilder);
            LOGGER.info("Loaded {} keys from {}", keyToKeyring.size(), keyRingFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<PGPPublicKey> findPublicKey(long id) {
        return Optional.ofNullable(keyToPublicKey.get(id));
    }

    @Override
    public Optional<PGPPublicKeyRing> findKeyRing(long id) {
        return Optional.ofNullable(keyToKeyring.get(id));
    }

    @Override
    public void close() {

    }
}
