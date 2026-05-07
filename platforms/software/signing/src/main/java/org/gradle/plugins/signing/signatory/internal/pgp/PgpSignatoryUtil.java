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

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.bc.BcPGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.jcajce.JcaPGPSecretKeyRing;
import org.bouncycastle.openpgp.jcajce.JcaPGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.gradle.api.InvalidUserDataException;
import org.gradle.internal.UncheckedException;
import org.gradle.plugins.signing.signatory.pgp.PgpKeyId;
import org.gradle.plugins.signing.signatory.pgp.PgpSignatoryFactory;
import org.jspecify.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.gradle.internal.Cast.uncheckedCast;
import static org.gradle.internal.IoActions.uncheckedClose;

/**
 * The internal implementation of PGP signatory handling.
 * This class exists to share implementation between {@link PgpSignatoryService} and {@link PgpSignatoryFactory}.
 */
public class PgpSignatoryUtil {
    private PgpSignatoryUtil() {}

    /**
     * Loads secret key from a keyring collection file by id.
     * <p>
     * Loading the key is an expensive operation.
     *
     * @param keyId the id of the key
     * @param file the keyring collection file
     * @return the parsed secret key
     * @throws InvalidUserDataException if the key cannot be found or loaded
     */
    public static PGPSecretKey readSecretKey(String keyId, File file) {
        InputStream inputStream = openSecretKeyFile(file);
        try {
            return readSecretKey(inputStream, keyId, "file: " + file.getAbsolutePath());
        } finally {
            uncheckedClose(inputStream);
        }
    }

    private static InputStream openSecretKeyFile(File file) {
        try {
            return new BufferedInputStream(new FileInputStream(file));
        } catch (FileNotFoundException e) {
            throw new InvalidUserDataException("Unable to retrieve secret key from key ring file '" + file + "' as it does not exist");
        }
    }

    /**
     * This method is public to support {@code PgpSignatoryFactory#readSecretKey(InputStream, String, String)}.
     * Do not use outside this class.
     */
    public static PGPSecretKey readSecretKey(InputStream input, String keyId, String sourceDescription) {
        PGPSecretKeyRingCollection keyRingCollection;
        try {
            keyRingCollection = new BcPGPSecretKeyRingCollection(input);
        } catch (Exception e) {
            throw new InvalidUserDataException("Unable to read secret key from " + sourceDescription + " (it may not be a PGP secret key ring)", e);
        }
        return readSecretKey(keyRingCollection, normalizeKeyId(keyId), sourceDescription);
    }

    /**
     * This method is public to support {@code PgpSignatoryFactory#normalizeKeyId(String)}.
     * Do not use outside this class.
     */
    public static PgpKeyId normalizeKeyId(String keyId) {
        try {
            return new PgpKeyId(keyId);
        } catch (IllegalArgumentException e) {
            throw new InvalidUserDataException(e.getMessage());
        }
    }

    /**
     * This method is public to support {@code PgpSignatoryFactory#readSecretKey(PGPSecretKeyRingCollection, PgpKeyId, String)}.
     * Do not use outside this class.
     */
    public static PGPSecretKey readSecretKey(PGPSecretKeyRingCollection keyRings, final PgpKeyId keyId, String sourceDescription) {
        PGPSecretKey key = findSecretKey(keyRings, keyId);
        if (key == null) {
            throw new InvalidUserDataException("did not find secret key for id '" + keyId + "' in key source '" + sourceDescription + "'");
        }
        return key;
    }

    private static @Nullable PGPSecretKey findSecretKey(PGPSecretKeyRingCollection keyRings, PgpKeyId keyId) {
        Iterator<PGPSecretKeyRing> keyRingIterator = uncheckedCast(keyRings.getKeyRings());
        while (keyRingIterator.hasNext()) {
            PGPSecretKeyRing keyRing = keyRingIterator.next();
            Iterator<PGPSecretKey> secretKeyIterator = uncheckedCast(keyRing.getSecretKeys());
            while (secretKeyIterator.hasNext()) {
                PGPSecretKey secretKey = secretKeyIterator.next();
                if (hasId(keyId, secretKey)) {
                    return secretKey;
                }
            }
        }
        return null;
    }

    private static boolean hasId(PgpKeyId keyId, PGPSecretKey secretKey) {
        return new PgpKeyId(secretKey.getKeyID()).equals(keyId);
    }

    /**
     * Extracts private key from a secret key using provided password.
     *
     * @param secretKey the secret key that contains the private key
     * @param password the password
     * @return the decrypted private key
     * @throws RuntimeException if the key cannot be decrypted
     */
    public static PGPPrivateKey extractPrivateKey(PGPSecretKey secretKey, String password) {
        try {
            PBESecretKeyDecryptor decryptor = new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider()).build(password.toCharArray());
            return secretKey.extractPrivateKey(decryptor);
        } catch (PGPException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    /**
     * Parses the secret key in ASCII-armored format.
     * The {@code keyData} may contain one or more master keys with subkeys.
     * If there is more than one master key or a subkey is needed, the {@code keyId} must be provided.
     * If omitted, the keyData is treated as a single master key (with subkeys) and the master secret key is returned.
     *
     * @param keyId the id of the key, can be null to return the only master secret key
     * @param keyData the ASCII-armored representation of the key
     * @return the parsed secret key
     */
    public static PGPSecretKey parseSecretKey(@Nullable String keyId, String keyData) {
        try (InputStream in = PGPUtil.getDecoderStream(new ByteArrayInputStream(keyData.getBytes(UTF_8)))) {
            if (keyId == null) {
                return new JcaPGPSecretKeyRing(in).getSecretKey();
            } else {
                PgpKeyId expectedKeyId = new PgpKeyId(keyId);
                PGPSecretKey key = findSecretKey(new JcaPGPSecretKeyRingCollection(in), expectedKeyId);
                if (key != null) {
                    return key;
                }
                throw new InvalidUserDataException(String.format("Cannot find key with id '%s' in key data",  keyId));
            }
        } catch (IOException | PGPException e) {
            throw new InvalidUserDataException("Could not read PGP secret key", e);
        }
    }
}
