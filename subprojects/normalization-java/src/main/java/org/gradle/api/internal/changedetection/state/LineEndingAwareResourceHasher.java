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
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.fingerprint.hashing.RegularFileSnapshotContext;
import org.gradle.internal.fingerprint.hashing.ResourceHasher;
import org.gradle.internal.fingerprint.hashing.ZipEntryContext;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.hash.PrimitiveHasher;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Iterator;

/**
 * A {@link ResourceHasher} that ignores line endings while hashing the file.  It detects whether a file is text or binary and only
 * normalizes line endings for text files.  If a file is detected to be binary, we fall back to the existing non-normalized hash.
 */
public class LineEndingAwareResourceHasher implements ResourceHasher {
    private final ResourceHasher delegate;
    private final LineEndingSensitivity lineEndingSensitivity;

    public LineEndingAwareResourceHasher(ResourceHasher delegate, LineEndingSensitivity lineEndingSensitivity) {
        this.delegate = delegate;
        this.lineEndingSensitivity = lineEndingSensitivity;
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        delegate.appendConfigurationToHasher(hasher);
        hasher.putString(getClass().getName());
        hasher.putString(lineEndingSensitivity.name());
    }

    @Nullable
    @Override
    public HashCode hash(RegularFileSnapshotContext snapshotContext) throws IOException {
        return lineEndingSensitivity.isCandidate(snapshotContext.getSnapshot()) ?
            hashContent(snapshotContext) :
            delegate.hash(snapshotContext);
    }

    @Nullable
    @Override
    public HashCode hash(ZipEntryContext zipEntryContext) throws IOException {
        return lineEndingSensitivity.isCandidate(zipEntryContext) ?
            hashContent(zipEntryContext) :
            delegate.hash(zipEntryContext);
    }

    @Nullable
    private HashCode hashContent(RegularFileSnapshotContext snapshotContext) throws IOException {
        try {
            return hashContent(new File(snapshotContext.getSnapshot().getAbsolutePath()));
        } catch (BinaryContentDetectedException e) {
            return delegate.hash(snapshotContext);
        }
    }

    @Nullable
    private HashCode hashContent(ZipEntryContext zipEntryContext) throws IOException {
        return zipEntryContext.getEntry().withInputStream(inputStream -> {
            try {
                return hashContent(inputStream);
            } catch (BinaryContentDetectedException e) {
                return delegate.hash(zipEntryContext);
            }
        });
    }

    private HashCode hashContent(InputStream inputStream) throws BinaryContentDetectedException {
        return new LineEndingAwareInputStreamHasher().hash(inputStream);
    }

    private HashCode hashContent(File file) throws BinaryContentDetectedException, IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            return hashContent(inputStream);
        }
    }

    private static class BinaryContentDetectedException extends Exception {
    }

    @VisibleForTesting
    static class LineEndingAwareInputStreamHasher {
        private static final HashCode SIGNATURE = Hashing.signature(LineEndingAwareInputStreamHasher.class);
        int peekAhead = -1;

        public HashCode hash(InputStream inputStream) throws BinaryContentDetectedException {
            byte[] buffer = new byte[8192];
            PrimitiveHasher hasher = Hashing.newPrimitiveHasher();

            try {
                hasher.putHash(SIGNATURE);
                while (true) {
                    int nread = read(inputStream, buffer);
                    if (nread < 0) {
                        break;
                    }

                    if (checkForControlCharacters(buffer, nread)) {
                        throw new BinaryContentDetectedException();
                    }

                    hasher.putBytes(buffer, 0, nread);
                }
                return hasher.hash();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to create MD5 hash for file content.", e);
            }
        }

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
