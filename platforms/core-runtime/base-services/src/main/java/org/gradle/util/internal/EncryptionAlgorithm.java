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

import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.io.IOException;

/**
 * A protocol for encryption algorithms.
 */
public interface EncryptionAlgorithm {
    String getTransformation();

    String getAlgorithm();

    /**
     * Creates an encryption/decryption session with this algorithm and the given key.
     *
     * @param key the key to use
     * @return a new encryption/decryption session
     */
    Session newSession(@Nullable SecretKey key);

    interface IVLoader {
        void load(byte[] toLoad) throws IOException;
    }

    interface IVCollector {
        void collect(byte[] toCollect) throws IOException;
    }

    /**
     * Combines an algorithm and a key, and allows obtaining encryption/decryption ciphers according to those.
     */
    interface Session {
        SecretKey getKey();
        EncryptionAlgorithm getAlgorithm();
        Cipher encryptingCipher(IVCollector collector);
        Cipher decryptingCipher(IVLoader loader);
    }

    class EncryptionException extends RuntimeException {
        public EncryptionException(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
        public EncryptionException(Throwable cause) {
            super(null, cause);
        }
    }

    EncryptionAlgorithm NONE = new EncryptionAlgorithm() {
        @Override
        public String getTransformation() {
            return "none";
        }

        @Override
        public String getAlgorithm() {
            return "none";
        }

        @Override
        public Session newSession(SecretKey key) {
            throw new IllegalStateException("Encryption not enabled");
        }
    };
}
