/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.cache.internal.btree;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class KeyHasher<K> {
    private final Serializer<K> serializer;
    private final ThreadLocal<State> state = ThreadLocal.withInitial(State::new);

    public KeyHasher(Serializer<K> serializer) {
        this.serializer = serializer;
    }

    long getHashCode(K key) throws Exception {
        State s = state.get();
        serializer.write(s.encoder, key);
        s.encoder.flush();
        return s.digestStream.getChecksum();
    }

    private static class State {
        final MessageDigestStream digestStream = new MessageDigestStream();
        final KryoBackedEncoder encoder = new KryoBackedEncoder(digestStream);
    }

    private static class MessageDigestStream extends OutputStream {
        MessageDigest messageDigest;

        private MessageDigestStream() {
            try {
                messageDigest = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        @Override
        public void write(int b) throws IOException {
            messageDigest.update((byte) b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            messageDigest.update(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            messageDigest.update(b, off, len);
        }

        long getChecksum() {
            byte[] d = messageDigest.digest();
            return ((long) (d[8] & 0xFF) << 56)
                | ((long) (d[9] & 0xFF) << 48)
                | ((long) (d[10] & 0xFF) << 40)
                | ((long) (d[11] & 0xFF) << 32)
                | ((long) (d[12] & 0xFF) << 24)
                | ((long) (d[13] & 0xFF) << 16)
                | ((long) (d[14] & 0xFF) << 8)
                | ((long) (d[15] & 0xFF));
        }
    }
}
