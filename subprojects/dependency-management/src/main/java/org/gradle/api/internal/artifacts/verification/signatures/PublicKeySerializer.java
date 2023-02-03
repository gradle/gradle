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
package org.gradle.api.internal.artifacts.verification.signatures;

import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.ByteArrayInputStream;

class PublicKeySerializer extends AbstractSerializer<PGPPublicKey> {

    @Override
    public PGPPublicKey read(Decoder decoder) throws Exception {
        byte[] encoded = decoder.readBinary();
        PGPObjectFactory objectFactory = new PGPObjectFactory(
            PGPUtil.getDecoderStream(new ByteArrayInputStream(encoded)), new BcKeyFingerprintCalculator());
        Object object = objectFactory.nextObject();
        if (object instanceof PGPPublicKey) {
            return (PGPPublicKey) object;
        } else if (object instanceof PGPPublicKeyRing) {
            return ((PGPPublicKeyRing) object).getPublicKey();
        }
        throw new IllegalStateException("Unexpected key in cache: " + object.getClass());
    }

    @Override
    public void write(Encoder encoder, PGPPublicKey key) throws Exception {
        encoder.writeBinary(key.getEncoded());
    }
}
