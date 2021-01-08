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

package org.gradle.security.fixtures

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.openpgp.PGPPrivateKey
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureGenerator
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.gradle.plugins.signing.signatory.pgp.PgpSignatoryFactory
import org.gradle.plugins.signing.type.pgp.ArmoredSignatureType

@CompileStatic
@Canonical
class SimpleKeyRing {
    final String name
    final PGPSecretKey secretKey
    final PGPPublicKey publicKey
    final String password

    void writePublicKeyRingTo(File file) {
        file.withOutputStream { out ->
            new PGPPublicKeyRingCollection(
                [new PGPPublicKeyRing([publicKey])]
            ).encode(out)
        }
    }

    File sign(File toSign) {
        def armored = new ArmoredSignatureType()
        def signatory = new PgpSignatoryFactory()
            .createSignatory(name, secretKey, password)
        return armored.sign(signatory, toSign)
    }

    File sign(File toSign, Map<PGPSecretKey, String> additionalKeys) {
        List<PGPSignatureGenerator> generators = []
        generators << createGenerator(secretKey, password)
        additionalKeys.each { secretKey, password ->
            generators << createGenerator(secretKey, password)
        }
        toSign.withInputStream {
            def buffer = new byte[1024]
            int len
            while ((len = it.read(buffer))>=0) {
                generators.each { generator ->
                    generator.update(buffer, 0, len)
                }
            }
        }
        def signed = new File(toSign.parentFile, "${toSign.name}.asc")
        signed.newOutputStream().withCloseable { stream ->
            new ArmoredOutputStream(stream).withCloseable { out ->
                generators.each {
                    it.generate().encode(out)
                }
            }
        }
        signed
    }

    private static PGPSignatureGenerator createGenerator(PGPSecretKey secretKey, String password) {
        PGPSignatureGenerator generator = new PGPSignatureGenerator(new BcPGPContentSignerBuilder(secretKey.getPublicKey().getAlgorithm(), PGPUtil.SHA512))
        generator.init(PGPSignature.BINARY_DOCUMENT, getPrivateKey(secretKey, password))
        generator
    }

    private static PGPPrivateKey getPrivateKey(PGPSecretKey secretKey, String password) {
        def decryptor = new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider())
            .build(password.toCharArray())
        return secretKey.extractPrivateKey(decryptor)
    }
}
