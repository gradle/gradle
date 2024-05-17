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

package org.gradle.util.internal;

import javax.annotation.Nonnull;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

/**
 * Encryption algorithms required/used in Gradle.
 */
public enum SupportedEncryptionAlgorithm implements EncryptionAlgorithm {
    AES_CBC_PADDING("AES/CBC/PKCS5PADDING", "AES", 16),
    AES_ECB_PADDING("AES/ECB/PKCS5PADDING", "AES", 0);
    private final String transformation;
    private final int initVectorLength;
    private final String algorithm;

    SupportedEncryptionAlgorithm(String transformation, String algorithm, int initVectorLength) {
        this.transformation = transformation;
        this.algorithm = algorithm;
        this.initVectorLength = initVectorLength;
    }
    private AlgorithmParameterSpec getDecryptionParameter(IVLoader ivLoader) throws IOException {
        assert initVectorLength > 0;
        byte[] initVector = new byte[initVectorLength];
        ivLoader.load(initVector);
        return createParameter(initVector);
    }

    private AlgorithmParameterSpec getEncryptionParameter(byte[] initVector, IVCollector ivCollector) throws IOException {
        assert initVector != null;
        assert initVector.length == initVectorLength;
        ivCollector.collect(initVector);
        return createParameter(initVector);
    }

    @Override
    public String getTransformation() {
        return transformation;
    }

    @Nonnull
    private static AlgorithmParameterSpec createParameter(@Nonnull byte[] initVector) {
        return new IvParameterSpec(initVector);
    }

    private Cipher encryptingCipher(SecretKey key, IVCollector collector) throws EncryptionException {
        try {
            Cipher newCipher = Cipher.getInstance(transformation);
            newCipher.init(Cipher.ENCRYPT_MODE, key);
            if (initVectorLength > 0) {
                assert collector != null;
                byte[] iv = newCipher.getIV();
                assert iv != null;
                collector.collect(iv);
            }
            return newCipher;
        } catch (IOException|GeneralSecurityException e) {
            throw new EncryptionException(e);
        }
    }

    private Cipher decryptingCipher(SecretKey key, IVLoader loader) throws EncryptionException {
        try {
            Cipher newCipher = Cipher.getInstance(transformation);
            if (initVectorLength > 0) {
                assert loader != null;
                newCipher.init(Cipher.DECRYPT_MODE, key, getDecryptionParameter(loader));
            } else {
                newCipher.init(Cipher.DECRYPT_MODE, key);
            }
            return newCipher;
        } catch (GeneralSecurityException|IOException e) {
            throw new EncryptionException(e);
        }
    }

    @Override
    public String getAlgorithm() {
        return algorithm;
    }

    @Override
    public Session newSession(SecretKey key) {
        return new Session() {
            @Override
            public Cipher decryptingCipher(IVLoader loader) {
                return SupportedEncryptionAlgorithm.this.decryptingCipher(key, loader);
            }

            @Override
            public Cipher encryptingCipher(IVCollector collector) {
                return SupportedEncryptionAlgorithm.this.encryptingCipher(key, collector);
            }

            @Override
            public EncryptionAlgorithm getAlgorithm() {
                return SupportedEncryptionAlgorithm.this;
            }

            @Override
            public SecretKey getKey() {
                return key;
            }
        };
    }

    @Nonnull
    public static EncryptionAlgorithm byTransformation(String transformation) {
        return Arrays.stream(values())
            .filter(it -> it.transformation.equals(transformation))
            .findFirst()
            .map(it -> (EncryptionAlgorithm) it)
            .orElse(SupportedEncryptionAlgorithm.NONE);
    }
}
