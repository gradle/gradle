/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.fingerprint.hashing.ZipEntryContext;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.hash.PrimitiveHasher;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Supplier;

public abstract class AbstractLineEndingAwareHasher {
    protected Optional<HashCode> hashContent(ZipEntryContext zipEntryContext) throws IOException {
        return zipEntryContext.getEntry().isDirectory() ? Optional.empty() : zipEntryContext.getEntry().withInputStream(this::hashContent);
    }

    private Optional<HashCode> hashContent(InputStream inputStream) throws IOException {
        return new LineEndingAwareInputStreamHasher().hash(inputStream);
    }

    protected Optional<HashCode> hashContent(File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            return hashContent(inputStream);
        }
    }

    /**
     * Provides convenience methods that allow providing a supplier that throws an exception.
     */
    protected static class Suppliers {
        // TODO - This is probably more generally usable, but there currently is no dependency project
        // that it makes sense to put this in.

        public static <T, E extends Exception> Supplier<T> rethrow(ThrowingSupplier<T, E> supplier) {
            return () -> get(wrap(supplier));
        }

        @SuppressWarnings("unchecked")
        @Nullable
        public static <T, E extends Exception> T get(Supplier<T> supplier) throws E {
            try {
                return supplier.get();
            } catch (WrappedException e) {
                throw (E)e.rethrow();
            }
        }

        public static <T, E extends Exception> Supplier<T> wrap(ThrowingSupplier<T, E> supplier) {
            return () -> {
                try {
                    return supplier.get();
                } catch (Exception e) {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException)e;
                    } else {
                        throw new WrappedException(e);
                    }
                }
            };
        }

        @FunctionalInterface
        public interface ThrowingSupplier<T, E extends Exception> {
            @Nullable
            T get() throws E;
        }

        private static class WrappedException extends RuntimeException {
            public WrappedException(Throwable cause) {
                super(cause);
            }

            @SuppressWarnings("unchecked")
            public <T extends Exception> T rethrow() {
                return (T)getCause();
            }
        }
    }

    @VisibleForTesting
    static class LineEndingAwareInputStreamHasher {
        private static final HashCode SIGNATURE = Hashing.signature(LineEndingAwareInputStreamHasher.class);
        private static final int BUFFER_SIZE = 8192;
        private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer original = ByteBuffer.allocate(BUFFER_SIZE);
        private final byte[] readBuffer = new byte[BUFFER_SIZE];
        private int peekAhead = -1;

        /**
         * Returns empty if the file is detected to be a binary file
         */
        Optional<HashCode> hash(InputStream inputStream) throws IOException {
            PrimitiveHasher hasher = Hashing.newPrimitiveHasher();

            hasher.putHash(SIGNATURE);

            while (true) {
                fillBuffer(inputStream, buffer);
                if (!buffer.hasRemaining()) {
                    break;
                }

                if (checkForControlCharacters(buffer.array(), buffer.limit())) {
                    return Optional.empty();
                }

                hasher.putBytes(buffer.array(), 0, buffer.limit());
                buffer.clear();
            }

            return Optional.of(hasher.hash());
        }

        @VisibleForTesting
        void fillBuffer(InputStream inputStream, ByteBuffer buffer) throws IOException {
            int readLimit = buffer.capacity();

            // If there is something left over in the peekAhead buffer, use that as the first byte
            if (peekAhead != -1) {
                readLimit--;
                original.put((byte)peekAhead);
                peekAhead = -1;
            }

            // Fill the original byte buffer with bytes from the input stream
            int read = inputStream.read(readBuffer, 0, readLimit);
            if (read != -1) {
                original.put(readBuffer, 0, read);
            }

            // Transfer the original bytes to the normalized byte buffer
            original.flip();
            while (original.hasRemaining()) {
                // Get our next byte from the peek ahead buffer if it contains anything
                int next = peekAhead;

                // If there was something in the peek ahead buffer, use it, otherwise get the next byte
                if (next != -1) {
                    peekAhead = -1;
                } else {
                    next = original.get();
                }

                // If the next bytes are '\r' or '\r\n', replace it with '\n'
                if (next == '\r') {
                    peekAhead = original.hasRemaining() ? original.get() : inputStream.read();
                    if (peekAhead == '\n') {
                        peekAhead = -1;
                    }
                    next = '\n';
                }

               buffer.put((byte) next);
            }

            // Flip the normalized buffer in preparation for reading and clear the original byte stream
            buffer.flip();
            original.clear();
        }

        private boolean checkForControlCharacters(byte[] buffer, int length) {
            for (int i = 0; i < length; i++) {
                if (isControlCharacter(buffer[i])) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isControlCharacter(int c) {
            return isInControlRange(c) && isNotCommonTextChar(c);
        }

        private static boolean isInControlRange(int c) {
            return c >= 0x00 && c < 0x20;
        }

        private static boolean isNotCommonTextChar(int c) {
            return !Character.isWhitespace(c);
        }
    }
}
