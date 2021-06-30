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
import com.google.common.primitives.Bytes;
import org.gradle.internal.fingerprint.hashing.ZipEntryContext;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.hash.PrimitiveHasher;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Supplier;

public abstract class AbstractLineEndingAwareHasher {
    protected Optional<HashCode> hashContent(ZipEntryContext zipEntryContext) throws IOException {
        return zipEntryContext.getEntry().isDirectory() ? Optional.empty() : zipEntryContext.getEntry().withInputStream(this::hashContent);
    }

    private Optional<HashCode> hashContent(InputStream inputStream) throws IOException {
        return new LineEndingAwareResourceHasher.LineEndingAwareInputStreamHasher().hash(inputStream);
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
        int peekAhead = -1;

        /**
         * Returns empty if the file is detected to be a binary file
         */
        public Optional<HashCode> hash(InputStream inputStream) throws IOException {
            byte[] buffer = new byte[8192];
            PrimitiveHasher hasher = Hashing.newPrimitiveHasher();

            hasher.putHash(SIGNATURE);
            while (true) {
                int nread = read(inputStream, buffer);
                if (nread < 0) {
                    break;
                }

                if (checkForControlCharacters(buffer, nread)) {
                    return Optional.empty();
                }

                hasher.putBytes(buffer, 0, nread);
            }
            return Optional.of(hasher.hash());
        }

        @VisibleForTesting
        int read(InputStream inputStream, byte[] b) throws IOException {
            byte[] original = new byte[b.length];

            // If there is something left over in the peekAhead buffer, use that as the first byte
            int readLimit = b.length;
            if (peekAhead != -1) {
                readLimit--;
            }

            int index = 0;
            int read = inputStream.read(original, 0, readLimit);
            if (read != -1) {
                Iterator<Byte> itr = Bytes.asList(original).subList(0, read).iterator();
                while (itr.hasNext()) {
                    // Get our next byte from the peek ahead buffer if it contains anything
                    int next = peekAhead;

                    // If there was something in the peek ahead buffer, use it, otherwise get the next byte
                    if (next != -1) {
                        peekAhead = -1;
                    } else {
                        next = itr.next();
                    }

                    // If the next bytes are '\r' or '\r\n', replace it with '\n'
                    if (next == '\r') {
                        peekAhead = itr.hasNext() ? itr.next() : inputStream.read();
                        if (peekAhead == '\n') {
                            peekAhead = -1;
                        }
                        next = '\n';
                    }

                    b[index++] = (byte) next;
                }
            } else if (peekAhead != -1) {
                // If there is a character still left in the peekAhead buffer but not in the input stream, then normalize it and return
                int next = peekAhead;
                peekAhead = -1;
                if (next == '\r') {
                    next = '\n';
                }
                b[index++] = (byte) next;
            }
            return index == 0 ? -1 : index;
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
