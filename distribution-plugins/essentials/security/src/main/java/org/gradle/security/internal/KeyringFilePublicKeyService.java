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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class KeyringFilePublicKeyService implements PublicKeyService {
    private final static Logger LOGGER = Logging.getLogger(KeyringFilePublicKeyService.class);

    private final Map<Fingerprint, PGPPublicKeyRing> keyToKeyring;
    private final Multimap<Long, PGPPublicKeyRing> longIdToPublicKeys;

    public KeyringFilePublicKeyService(File keyRingFile) {
        try {
            List<PGPPublicKeyRing> keyrings = SecuritySupport.loadKeyRingFile(keyRingFile);
            Map<Fingerprint, PGPPublicKeyRing> keyToKeyringBuilder = Maps.newHashMap();
            ImmutableMultimap.Builder<Long, PGPPublicKeyRing> longIdLongPGPPublicKeyBuilder = ImmutableListMultimap.builder();

            for (PGPPublicKeyRing keyring : keyrings) {
                Iterator<PGPPublicKey> it = keyring.getPublicKeys();
                while (it.hasNext()) {
                    PGPPublicKey key = it.next();
                    Fingerprint fingerprint = Fingerprint.of(key);
                    keyToKeyringBuilder.put(fingerprint, keyring);
                    longIdLongPGPPublicKeyBuilder.put(key.getKeyID(), keyring);
                }
            }
            keyToKeyring = ImmutableMap.copyOf(keyToKeyringBuilder);
            longIdToPublicKeys = longIdLongPGPPublicKeyBuilder.build();
            LOGGER.info("Loaded {} keys from {}", keyToKeyring.size(), keyRingFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {

    }

    @Override
    public void findByLongId(long keyId, PublicKeyResultBuilder builder) {
        for (PGPPublicKeyRing keyring : longIdToPublicKeys.get(keyId)) {
            builder.keyRing(keyring);
            Iterator<PGPPublicKey> pkIt = keyring.getPublicKeys();
            while (pkIt.hasNext()) {
                PGPPublicKey key = pkIt.next();
                if (key.getKeyID() == keyId) {
                    builder.publicKey(key);
                }
            }
        }
    }

    @Override
    public void findByFingerprint(byte[] bytes, PublicKeyResultBuilder builder) {
        Fingerprint fingerprint = Fingerprint.wrap(bytes);
        PGPPublicKeyRing keyring = keyToKeyring.get(fingerprint);
        if (keyring != null) {
            builder.keyRing(keyring);
            Iterator<PGPPublicKey> pkIt = keyring.getPublicKeys();
            while (pkIt.hasNext()) {
                PGPPublicKey key = pkIt.next();
                if (Arrays.equals(key.getFingerprint(), bytes)) {
                    builder.publicKey(key);
                }
            }
        }
    }
}
