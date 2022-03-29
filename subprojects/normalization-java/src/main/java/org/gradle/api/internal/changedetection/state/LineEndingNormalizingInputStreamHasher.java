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

import org.apache.commons.io.input.CloseShieldInputStream;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.hash.PrimitiveHasher;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Hashes input streams while normalizing line endings in text files.  Normalization involves treating '\r' and '\r\n' characters
 * as '\n' while calculating the hash.  If a file is detected to be binary (i.e. if the file contains ASCII control characters) then the hashing
 * returns an empty {@link Optional} object.  Non-ASCII encodings (e.g. UTF-16) are treated as binary files.
 */
public class LineEndingNormalizingInputStreamHasher {
    private static final HashCode SIGNATURE = Hashing.signature(LineEndingNormalizingInputStreamHasher.class);
    private static final int BUFFER_SIZE = 8192;

    /**
     * Hash the contents of the provided input stream, normalizing line endings.
     *
     * @param inputStream The input stream to hash
     * @return An {@link Optional} containing the {@link HashCode} or empty if the file is binary
     * @throws IOException
     */
    public Optional<HashCode> hashContent(InputStream inputStream) throws IOException {
        return hash(inputStream);
    }

    /**
     * Hash the contents of the provided file, normalizing line endings.
     *
     * @param file The file to hash
     * @return An {@link Optional} containing the {@link HashCode} or empty if the file is binary
     * @throws IOException
     */
    public Optional<HashCode> hashContent(File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            return hashContent(inputStream);
        }
    }

    /**
     * Returns empty if the file is detected to be a binary file
     */
    private Optional<HashCode> hash(InputStream inputStream) throws IOException {
        PrimitiveHasher hasher = Hashing.newPrimitiveHasher();

        hasher.putHash(SIGNATURE);

        try (BufferedInputStream input = new BufferedInputStream(CloseShieldInputStream.wrap(inputStream), BUFFER_SIZE)) {
            int peekAhead = -1;

            while (true) {
                // If there is something left over in the peekAhead buffer, use that
                int next = peekAhead;

                // If the peekAhead buffer is empty, get the next byte from the input stream
                if (next != -1) {
                    peekAhead = -1;
                } else {
                    next = input.read();
                }

                // If both the peekAhead buffer and the input stream are empty, we're done
                if (next == -1) {
                    break;
                }

                // Bust out if we detect a binary file
                if (isControlCharacter(next)) {
                    return Optional.empty();
                }

                // If the next bytes are '\r' or '\r\n', replace it with '\n'
                if (next == '\r') {
                    peekAhead = input.read();
                    if (peekAhead == '\n') {
                        peekAhead = -1;
                    }
                    next = '\n';
                }

                hasher.putByte((byte) next);
            }
        }

        return Optional.of(hasher.hash());
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
