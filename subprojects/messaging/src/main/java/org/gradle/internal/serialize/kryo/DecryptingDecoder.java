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

package org.gradle.internal.serialize.kryo;

import com.esotericsoftware.kryo.KryoException;
import org.gradle.util.internal.EncryptionAlgorithm;

import javax.crypto.Cipher;
import java.io.EOFException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.security.GeneralSecurityException;

public class DecryptingDecoder extends StringDeduplicatingKryoBackedDecoder {
    private final EncryptionAlgorithm.Session session;
    private Cipher cipher;

    public DecryptingDecoder(InputStream inputStream, EncryptionAlgorithm.Session session) {
        super(inputStream);
        this.session = session;
    }

    public DecryptingDecoder(InputStream inputStream, EncryptionAlgorithm.Session session, int bufferSize) {
        super(inputStream, bufferSize);
        this.session = session;
    }

    @Override
    public String readNullableString() throws EOFException {
        try {
            int idx = readInt();
            if (idx == -1) {
                return null;
            }
            if (strings == null) {
                strings = new String[INITIAL_CAPACITY];
            }
            String string = null;
            if (idx >= strings.length) {
                String[] grow = new String[strings.length * 3 / 2];
                System.arraycopy(strings, 0, grow, 0, strings.length);
                strings = grow;
            } else {
                string = strings[idx];
            }
            if (string == null) {
                string = readUniqueString();
                strings[idx] = string;
            }
            return string;
        } catch (KryoException e) {
            throw maybeEndOfStream(e);
        }
    }

    private String readUniqueString() {
        try {
            Cipher cipher = getCipher();
            int length = input.readInt();
            assert length >= 0 : String.format("string length = %d", length);
            assert length < 10 * 1024 * 1024 : String.format("string length = %d", length);
            byte[] asBytes = input.readBytes(length);
            byte[] decrypted = cipher.doFinal(asBytes);
            ByteBuffer buffer = ByteBuffer.wrap(decrypted);
            CharBuffer charBuffer = charsetDecoder().decode(buffer);
            return charBuffer.toString();
        } catch (GeneralSecurityException e) {
            throw new KryoException(e);
        } catch (CharacterCodingException e) {
            throw new KryoException(e);
        }
    }

    private CharsetDecoder charsetDecoder() {
        return Charset.forName("UTF-8")
            .newDecoder();
    }

    private Cipher getCipher() {
        if (cipher != null) {
            return cipher;
        }
        return cipher = session.decryptingCipher(new EncryptionAlgorithm.IVLoader() {
            @Override
            public void load(byte[] toLoad) {
                input.read(toLoad);
            }
        });
    }

}
