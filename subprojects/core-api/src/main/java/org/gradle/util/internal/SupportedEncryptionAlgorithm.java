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

import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import org.gradle.api.NonNullApi;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Set;

/**
 * Encryption algorithms required/used in Gradle.
 */
@NonNullApi
public class SupportedEncryptionAlgorithm {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final EncryptionAlgorithm AES_GCM_NO_PADDING =
        new DefaultEncryptionAlgorithm<>("AES/GCM/NoPadding", RANDOM, new GcmAlgorithmParameters(128));
    private static final EncryptionAlgorithm AES_CTR_NO_PADDING =
        new DefaultEncryptionAlgorithm<>("AES/CTR/NoPadding", RANDOM, new IvOnlyAlgorithmParameters());

    /**
     * Get the default cipher.
     */
    public static EncryptionAlgorithm getDefault() {
        return AES_CTR_NO_PADDING;
    }

    /**
     * Get all ciphers officially supported by Gradle.
     */
    public static Set<EncryptionAlgorithm> getAll() {
        return ImmutableSet.of(AES_GCM_NO_PADDING, AES_CTR_NO_PADDING);
    }

    /**
     * A general-purpose cypher implementation.
     *
     * @param <P> The type of the algorithm parameters.
     */
    private static class DefaultEncryptionAlgorithm<P extends AlgorithmParameterSpec> implements EncryptionAlgorithm {

        private final String transformation;
        private final SecureRandom random;
        private final AlgorithmParameters<P> parameters;

        public DefaultEncryptionAlgorithm(String transformation, SecureRandom random, AlgorithmParameters<P> parameters) {
            this.transformation = transformation;
            this.random = random;
            this.parameters = parameters;
        }

        @Override
        public final InputStream decryptedStream(InputStream inputStream, SecretKey key) throws GeneralSecurityException, IOException {
            Cipher cipher = Cipher.getInstance(transformation);
            int blockSize = cipher.getBlockSize();
            P params = parameters.read(inputStream, blockSize);
            cipher.init(Cipher.DECRYPT_MODE, key, params);
            return new CipherInputStream(inputStream, cipher);
        }

        @Override
        public final OutputStream encryptedStream(OutputStream outputStream, SecretKey key) throws GeneralSecurityException, IOException {
            Cipher cipher = Cipher.getInstance(transformation);
            int blockSize = cipher.getBlockSize();
            P params = parameters.write(outputStream, random, blockSize);
            cipher.init(Cipher.ENCRYPT_MODE, key, params);
            return new CipherOutputStream(outputStream, cipher);
        }

        @Override
        public final String getTransformation() {
            return transformation;
        }

        @Override
        public final String getAlgorithm() {
            return transformation.substring(0, transformation.indexOf('/'));
        }
    }

    /**
     * Reads and writes algorithm parameters to the provided input/output streams.
     */
    interface AlgorithmParameters<P extends AlgorithmParameterSpec> {

        /**
         * Read algorithm parameters from the input stream, returning the result.
         */
        P read(InputStream inputStream, int blockSize) throws IOException;

        /**
         * Create a new set of algorithm parameters, writing them to the output stream and returning the result.
         */
        P write(OutputStream outputStream, SecureRandom random, int blockSize) throws IOException;
    }

    /**
     * Algorithm parameter implementation for GCM-based transformations.
     *
     * Below are examples of transformations that this implementation can handle:
     * <ul>
     *     <li>AES/GCM/NoPadding</li>
     * </ul>
     */
    private static class GcmAlgorithmParameters implements AlgorithmParameters<GCMParameterSpec> {

        /**
         * The length of the IV, in bytes. According to NIST SP 800-38D, the IV length should be
         * 96 bits (12 bytes).
         */
        private static final int IV_LENGTH_BYTES = 12;

        private final int tagLengthBits;

        public GcmAlgorithmParameters(int tagLengthBits) {
            this.tagLengthBits = tagLengthBits;
        }

        @Override
        public GCMParameterSpec read(InputStream inputStream, int blockSize) throws IOException {
            byte[] iv = readNBytes(inputStream, IV_LENGTH_BYTES);
            return new GCMParameterSpec(tagLengthBits, iv);
        }

        @Override
        public GCMParameterSpec write(OutputStream outputStream, SecureRandom random, int blockSize) throws IOException {
            byte[] iv = generateIv(IV_LENGTH_BYTES, random);
            outputStream.write(iv);
            return new GCMParameterSpec(tagLengthBits, iv);
        }
    }

    /**
     * Algorithm parameter implementation for transformations that require a single IV.
     *
     * Below are examples of transformations that this implementation can handle:
     * <ul>
     *     <li>AES/CTR/NoPadding</li>
     *     <li>AES/CBC/PKCS5PADDING</li>
     * </ul>
     */
    private static class IvOnlyAlgorithmParameters implements AlgorithmParameters<IvParameterSpec> {
        @Override
        public IvParameterSpec read(InputStream inputStream, int blockSize) throws IOException {
            byte[] iv = readNBytes(inputStream, blockSize);
            return new IvParameterSpec(iv);
        }

        @Override
        public IvParameterSpec write(OutputStream outputStream, SecureRandom random, int blockSize) throws IOException {
            byte[] iv = generateIv(blockSize, random);
            outputStream.write(iv);
            return new IvParameterSpec(iv);
        }
    }

    /**
     * Generate an initialization vector of the given size for an encryption algorithm.
     */
    private static byte[] generateIv(int size, SecureRandom random){
        byte[] iv = new byte[size];
        random.nextBytes(iv);
        return iv;
    }

    /**
     * Read a fixed number of bytes from the provided input stream.
     */
    private static byte[] readNBytes(InputStream inputStream, int size) throws IOException {
        byte[] buf = new byte[size];
        ByteStreams.readFully(inputStream, buf);
        return buf;
    }
}
