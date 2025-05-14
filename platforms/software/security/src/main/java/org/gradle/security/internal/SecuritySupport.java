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

import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.KeyFingerPrintCalculator;
import org.bouncycastle.openpgp.operator.PGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.jspecify.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;

public class SecuritySupport {
    private static final Logger LOGGER = Logging.getLogger(SecuritySupport.class);

    private static final int BUFFER = 4096;
    public static final String KEYS_FILE_EXT = ".keys";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.setProperty("crypto.policy", "unlimited");
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public static boolean verify(File file, PGPSignature signature, PGPPublicKey publicKey) throws PGPException {
        signature.init(createContentVerifier(), publicKey);
        byte[] buffer = new byte[BUFFER];
        int len;
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            while ((len = in.read(buffer)) >= 0) {
                signature.update(buffer, 0, len);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return signature.verify();
    }

    private static PGPContentVerifierBuilderProvider createContentVerifier() {
        return new BcPGPContentVerifierBuilderProvider();
    }

    @Nullable
    public static PGPSignatureList readSignatures(File file) {
        try (
            InputStream stream = new BufferedInputStream(Files.newInputStream(file.toPath()));
            InputStream decoderStream = PGPUtil.getDecoderStream(stream)
        ) {
            return readSignatureList(decoderStream, file.toString());
        } catch (IOException | PGPException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Nullable
    private static PGPSignatureList readSignatureList(InputStream decoderStream, String locationHint) throws IOException, PGPException {
        PGPObjectFactory objectFactory = new PGPObjectFactory(decoderStream, new BcKeyFingerprintCalculator());
        Object nextObject = objectFactory.nextObject();
        if (nextObject instanceof PGPSignatureList) {
            return (PGPSignatureList) nextObject;
        } else if (nextObject instanceof PGPCompressedData) {
            return readSignatureList(((PGPCompressedData) nextObject).getDataStream(), locationHint);
        } else {
            LOGGER.warn("Expected a signature list in {}, but got {}. Skipping this signature.", locationHint, nextObject == null ? "invalid file" : nextObject.getClass());
            return null;
        }
    }

    public static String toLongIdHexString(long key) {
        return String.format("%016X", key).trim();
    }

    public static String toHexString(byte[] fingerprint) {
        return Fingerprint.wrap(fingerprint).toString();
    }

    public static List<PGPPublicKeyRing> loadKeyRingFile(File keyringFile) throws IOException {
        List<PGPPublicKeyRing> existingRings = new ArrayList<>();
        // load existing keys from keyring before
        try (InputStream ins = PGPUtil.getDecoderStream(createInputStreamFor(keyringFile))) {
            PGPObjectFactory objectFactory = new JcaPGPObjectFactory(ins);
            KeyFingerPrintCalculator fingerprintCalculator = new JcaKeyFingerprintCalculator();
            try {
                for (Object o : objectFactory) {
                    if (o instanceof PGPPublicKeyRing) {
                        // backward compatibility: old keyrings should be stripped too
                        PGPPublicKeyRing strippedKeyRing = KeyringStripper.strip((PGPPublicKeyRing) o, fingerprintCalculator);
                        existingRings.add(strippedKeyRing);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Error while reading the keyring file. {} keys read: {}", existingRings.size(), e.getMessage());
            }
        }
        return existingRings;
    }

    private static InputStream createInputStreamFor(File keyringFile) throws IOException {
        InputStream stream = new BufferedInputStream(new FileInputStream(keyringFile));
        if (keyringFile.getName().endsWith(KEYS_FILE_EXT)) {
            return new ArmoredInputStream(stream);
        }
        return stream;
    }
}
