/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.signing.signatory.pgp;

import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.UncheckedException;
import org.gradle.plugins.signing.signatory.SignatorySupport;
import org.gradle.security.internal.SecuritySupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * PGP signatory from PGP key and password.
 */
public class PgpSignatory extends SignatorySupport {

    static {
        SecuritySupport.assertInitialized();
    }

    private final String name;
    private final PGPSecretKey secretKey;
    private final PGPPrivateKey privateKey;

    public PgpSignatory(String name, PGPSecretKey secretKey, String password) {
        this.name = name;
        this.secretKey = secretKey;
        this.privateKey = createPrivateKey(secretKey, password);
    }

    @Override
    public final String getName() {
        return name;
    }

    /**
     * Exhausts {@code toSign}, and writes the signature to {@code signatureDestination}.
     *
     * The caller is responsible for closing the streams, though the output WILL be flushed.
     */
    @Override
    public void sign(InputStream toSign, OutputStream signatureDestination) {
        PGPSignatureGenerator generator = createSignatureGenerator();
        try {
            feedGeneratorWith(toSign, generator);

            PGPSignature signature = generator.generate();
            writeSignatureTo(signatureDestination, signature);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (PGPException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public String getKeyId() {
        PgpKeyId id = new PgpKeyId(secretKey.getKeyID());
        return id.getAsHex();
    }

    private void feedGeneratorWith(InputStream toSign, PGPSignatureGenerator generator) throws IOException {
        byte[] buffer = new byte[1024];
        int read = toSign.read(buffer);
        while (read > 0) {
            generator.update(buffer, 0, read);
            read = toSign.read(buffer);
        }
    }

    private void writeSignatureTo(OutputStream signatureDestination, PGPSignature pgpSignature) throws PGPException, IOException {
        // BCPGOutputStream seems to do some internal buffering, it's unclear whether it's strictly required here though
        BCPGOutputStream bufferedOutput = new BCPGOutputStream(signatureDestination);
        pgpSignature.encode(bufferedOutput);
        bufferedOutput.flush();
    }

    public PGPSignatureGenerator createSignatureGenerator() {
        try {
            PGPSignatureGenerator generator = new PGPSignatureGenerator(new BcPGPContentSignerBuilder(secretKey.getPublicKey().getAlgorithm(), PGPUtil.SHA512));
            generator.init(PGPSignature.BINARY_DOCUMENT, privateKey);
            return generator;
        } catch (PGPException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private PGPPrivateKey createPrivateKey(PGPSecretKey secretKey, String password) {
        try {
            PBESecretKeyDecryptor decryptor = new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider()).build(password.toCharArray());
            return secretKey.extractPrivateKey(decryptor);
        } catch (PGPException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
