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

import com.google.common.collect.Maps;
import org.gradle.util.internal.EncryptionAlgorithm;

import javax.annotation.Nullable;
import javax.crypto.Cipher;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.security.GeneralSecurityException;

public class EncryptingEncoder extends StringDeduplicatingKryoBackedEncoder {
    private final EncryptionAlgorithm.Session session;
    private Cipher cipher;

    public EncryptingEncoder(OutputStream outputStream, EncryptionAlgorithm.Session session) {
        super(outputStream);
        this.session = session;
    }

    public EncryptingEncoder(OutputStream outputStream, EncryptionAlgorithm.Session session, int bufferSize) {
        super(outputStream, bufferSize);
        this.session = session;
    }

    @Override
    public void writeNullableString(@Nullable CharSequence value) {
        if (value == null) {
            output.writeInt(-1);
            return;
        } else {
            if (strings == null) {
                strings = Maps.newHashMapWithExpectedSize(1024);
            }
        }
        String key = value.toString();
        Integer index = strings.get(key);
        if (index == null) {
            index = strings.size();
            output.writeInt(index);
            strings.put(key, index);
            writeUniqueString(key);
        } else {
            output.writeInt(index);
        }
    }

    private void writeUniqueString(String uniqueString) {
        try {
            Cipher cipher = getCipher();
            CharBuffer charBuffer = CharBuffer.wrap(uniqueString);
            ByteBuffer encoded = charsetEncoder().encode(charBuffer);
            byte[] encrypted = cipher.doFinal(encoded.array(), encoded.arrayOffset(), encoded.limit() - encoded.arrayOffset());
            output.writeInt(encrypted.length);
            output.write(encrypted);
        } catch (CharacterCodingException e) {
            throw new RuntimeException(e);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private CharsetEncoder charsetEncoder() {
        return Charset.forName("UTF-8")
            .newEncoder();
    }

    private Cipher getCipher() {
        if (cipher != null) {
            return cipher;
        }
        return cipher = session.encryptingCipher(new EncryptionAlgorithm.IVCollector() {
            @Override
            public void collect(byte[] toLoad) {
                output.write(toLoad);
            }
        });
    }
}
