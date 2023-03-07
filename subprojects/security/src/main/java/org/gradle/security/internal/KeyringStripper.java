/*
 * Copyright 2023 the original author or authors.
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
import org.bouncycastle.bcpg.PublicKeyPacket;
import org.bouncycastle.bcpg.TrustPacket;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.operator.KeyFingerPrintCalculator;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A utility class to strip unnecessary information from a keyring
 */
public class KeyringStripper {

    private static final Constructor<PGPPublicKey> SUBKEY_CONSTRUCTOR;

    static {
        try {
            SUBKEY_CONSTRUCTOR = getSubkeyConstructor();
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static PGPPublicKeyRing strip(PGPPublicKeyRing keyring, KeyFingerPrintCalculator fingerprintCalculator) {
        List<PGPPublicKey> strippedKeys = StreamSupport
            .stream(keyring.spliterator(), false)
            .map(key -> stripKey(key, fingerprintCalculator))
            .collect(Collectors.toList());

        return new PGPPublicKeyRing(strippedKeys);
    }

    @SuppressWarnings("unchecked")
    private static PGPPublicKey stripKey(PGPPublicKey key, KeyFingerPrintCalculator fingerprintCalculator) {
        try {
            if (key.isMasterKey()) {
                return new PGPPublicKey(key.getPublicKeyPacket(), fingerprintCalculator);
            } else {
                // unfortunately, the PGPPublicKey constructor is package private, so we need to use reflection
                return SUBKEY_CONSTRUCTOR.newInstance(
                    key.getPublicKeyPacket(),
                    null,
                    ImmutableList.copyOf(key.getKeySignatures()),
                    fingerprintCalculator
                );
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Constructor<PGPPublicKey> getSubkeyConstructor() throws NoSuchMethodException {
        Constructor<PGPPublicKey> constructor = PGPPublicKey.class.getDeclaredConstructor(
            PublicKeyPacket.class,
            TrustPacket.class,
            List.class,
            KeyFingerPrintCalculator.class
        );
        constructor.setAccessible(true);
        return constructor;
    }
}
