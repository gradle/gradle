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
package org.gradle.plugins.signing.signatory.pgp

import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPPrivateKey
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureGenerator
import org.bouncycastle.bcpg.BCPGOutputStream

import org.bouncycastle.jce.provider.BouncyCastleProvider

import java.security.Security

import org.gradle.plugins.signing.signatory.SignatorySupport

class PgpSignatory extends SignatorySupport {
    
    final String name
    final private String password
    final private PGPSecretKey secretKey
    final private PGPPrivateKey privateKey
    
    PgpSignatory(String name, PGPSecretKey secretKey, String password) {
        // ok to call multiple times, will be ignored
        Security.addProvider(new BouncyCastleProvider())
        
        this.name = name
        this.password = password
        this.secretKey = secretKey
        this.privateKey = secretKey.extractPrivateKey(password.toCharArray(), BouncyCastleProvider.PROVIDER_NAME)
    }

    PgpKeyId getKeyId() {
        new PgpKeyId(secretKey.keyID)
    }
    
    PGPSignatureGenerator createSignatureGenerator() {
        def generator = new PGPSignatureGenerator(secretKey.publicKey.algorithm, PGPUtil.SHA1, BouncyCastleProvider.PROVIDER_NAME)
        generator.initSign(PGPSignature.BINARY_DOCUMENT, privateKey)
        generator
    }
    
    /**
     * Exhausts {@code toSign}, and writes the signature to {@code signatureDestination}.
     * 
     * The caller is responsible for closing the streams, though the output WILL be flushed.
     */
    void sign(InputStream toSign, OutputStream signatureDestination) {
        def generator = createSignatureGenerator()
        
        def buffer = new byte[1024]
        def read = toSign.read(buffer)
        while (read > 0) {
            generator.update(buffer, 0, read)
            read = toSign.read(buffer)
        }

        // BCPGOutputStream seems to do some internal buffering, it's unclear whether it's stricly required here though
        def bufferedOutput = new BCPGOutputStream(signatureDestination)
        def signature = generator.generate()
        signature.encode(bufferedOutput)
        bufferedOutput.flush()
    }
    
}