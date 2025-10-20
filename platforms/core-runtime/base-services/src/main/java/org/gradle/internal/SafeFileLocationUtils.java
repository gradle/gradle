/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.internal;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Utf8;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import org.apache.commons.io.FileSystem;
import org.gradle.api.GradleException;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Sibling class to {@link FileUtils}, focused on obtaining safe file locations and names.
 */
public final class SafeFileLocationUtils {
    public static final int WINDOWS_PATH_LIMIT = 260;

    // SipHash-2-4 provides decent collision resistance of 64 bits while being fast to compute
    private static final HashFunction HASHER = Hashing.sipHash24();
    private static final BaseEncoding BASE_ENCODING = BaseEncoding.base32Hex().omitPadding();

    /**
     * A short, distinctive prefix to indicate that a file name has been truncated.
     *
     * <p>
     * Uses underscores as they are likely valid characters on all filesystems,
     * and visually distinctive. Uses "cut" instead of "truncated" to keep it short.
     * </p>
     */
    private static final byte[] TRUNCATED_PREFIX_BYTES = "_cut_".getBytes(StandardCharsets.UTF_8);

    /**
     * The maximum file name length in bytes for most filesystems (e.g. ext4, NTFS).
     *
     * <p>
     * We use a smaller limit on input, but emit this size for outputs with hashes.
     * </p>
     */
    private static final int MAX_FILE_NAME_LENGTH_IN_BYTES = 255;

    /**
     * The maximum safe file name length in bytes to avoid exceeding filesystem limits after adding a hash suffix.
     *
     * <p>
     * We subtract 1 byte for the hyphen that will separate the original name from the hash suffix.
     * We divide the bits by 5 with round up as we encode them as base32 to keep it case-insensitive.
     * </p>
     */
    @VisibleForTesting
    static final int MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES = MAX_FILE_NAME_LENGTH_IN_BYTES - TRUNCATED_PREFIX_BYTES.length - 1 - (HASHER.bits() + 4) / 5;

    /**
     * The character used to replace illegal characters in file names.
     */
    private static final char ILLEGAL_CHAR_REPLACEMENT = '-';

    /**
     * Converts a string into a string that is safe to use as a file name.
     * The result will preserve Unicode characters while replacing filesystem-illegal
     * and web-problematic characters with "-".
     *
     * <p>
     * Long strings will also be hashed to avoid issues with file name length limitations.
     * </p>
     */
    public static String toSafeFileName(String name) {
        String shortenedName = cleanAndShortenName(name);

        // Use Windows filesystem rules for cross-platform compatibility
        String result = FileSystem.WINDOWS.toLegalFileName(shortenedName, ILLEGAL_CHAR_REPLACEMENT);

        // Replace additional characters that may cause issues in web/HTML contexts
        return result.replace(' ', ILLEGAL_CHAR_REPLACEMENT)
            .replace('\t', ILLEGAL_CHAR_REPLACEMENT)
            .replace('\n', ILLEGAL_CHAR_REPLACEMENT)
            .replace('\r', ILLEGAL_CHAR_REPLACEMENT);
    }

    // CharsetEncoder is not thread-safe, so use ThreadLocal to hold one per thread
    // These are not very expensive memory-wise, so this should be OK
    private static final ThreadLocal<CharsetEncoder> UTF_8_ENCODER_WITH_ILLEGAL_CHAR_REPLACEMENT =
        ThreadLocal.withInitial(() ->
            StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .replaceWith(String.valueOf(ILLEGAL_CHAR_REPLACEMENT).getBytes(StandardCharsets.UTF_8))
        );

    /**
     * Remove malformed or un-mappable characters and shorten the name if necessary.
     *
     * @param name the original name
     * @return the cleaned and potentially shortened name
     */
    private static String cleanAndShortenName(String name) {
        byte[] rawName;
        try {
            ByteBuffer buffer = UTF_8_ENCODER_WITH_ILLEGAL_CHAR_REPLACEMENT.get().encode(CharBuffer.wrap(name));
            rawName = new byte[buffer.remaining()];
            buffer.get(rawName);
        } catch (CharacterCodingException e) {
            // We use the REPLACE action, so this should never happen
            throw new AssertionError("should never encounter an encoding error", e);
        }
        if (rawName.length <= MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES) {
            return name;
        }


        byte[] hashBytes = HASHER.hashBytes(rawName).asBytes();
        String encoded = BASE_ENCODING.encode(hashBytes);

        return shortenNameAndAddHash(rawName, encoded);
    }

    /**
     * Shorten the name to fit within {@code MAX_FILE_NAME_LENGTH_IN_BYTES} by truncating the original name
     * and appending a hyphen and the given encoded hash. If the original name has extension(s) shorter than
     * {@code MAX_FILE_NAME_LENGTH_IN_BYTES}, the extension(s) are preserved after the hash.
     *
     * @param rawName the original name in UTF-8 bytes
     * @param encoded the encoded hash string
     * @return the shortened name with hash inserted
     */
    private static String shortenNameAndAddHash(byte[] rawName, String encoded) {
        ByteBuffer result = ByteBuffer.allocate(MAX_FILE_NAME_LENGTH_IN_BYTES);
        // Insert before any potential file extensions
        byte[] extensions = null;
        int safeLength;
        int firstDot = findStartOfExtension(rawName);
        if (firstDot > 0) {
            extensions = Arrays.copyOfRange(rawName, firstDot, rawName.length);
            safeLength = getSafeLength(rawName, MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - extensions.length);
        } else {
            safeLength = getSafeLength(rawName, MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES);
        }
        // Copy truncated prefix
        result.put(TRUNCATED_PREFIX_BYTES);
        // Copy safe length of original name
        result.put(rawName, 0, safeLength);
        // Copy hyphen
        result.put((byte) '-');
        // Copy hash
        byte[] encodedBytes = encoded.getBytes(StandardCharsets.US_ASCII);
        result.put(encodedBytes);
        // Copy extensions if any
        if (extensions != null) {
            result.put(extensions);
        }
        // Decode back to string
        return new String(result.array(), 0, result.position(), StandardCharsets.UTF_8);
    }

    /**
     * Find the index of the first dot in {@code rawName} that is likely to indicate the start of file extension(s),
     * but only if the extension(s) can fit within {@code MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES} from the end.
     *
     * @param rawName the original name in UTF-8 bytes
     * @return the index of the first dot indicating the start of extension(s), or {@code -1} if none found
     */
    private static int findStartOfExtension(byte[] rawName) {
        // We look for the first dot that is within (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - 1) from the end
        // This ensures that the extension can fit even after adding the hyphen and hash
        // Also start at minimum 1 to avoid treating a leading dot as an extension
        int start = Math.max(1, rawName.length - (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - 1));
        // Search only until the second last character to avoid treating a trailing dot as an extension
        for (int i = start; i < rawName.length - 1; i++) {
            // Doing a raw byte comparison for '.' is safe as UTF-8 ensures that no other character will encode to contain it
            if (rawName[i] == '.') {
                return i;
            }
        }
        return -1;
    }

    /**
     * Get the index into {@code rawName} that will keep the size in UTF-8 bytes less than {@code maxBytes},
     * but still preserve valid UTF-8 encoding boundaries.
     *
     * @param rawName the original name
     * @param maxBytes the maximum number of bytes
     * @return the substring of name that encodes to less than {@code maxBytes} in UTF-8
     */
    private static int getSafeLength(byte[] rawName, int maxBytes) {
        if (rawName.length <= maxBytes) {
            return rawName.length;
        }
        // Walk backwards to find the byte that is the start of a code point (i.e. not 10xx xxxx)
        int startOfCodePoint = maxBytes - 1;
        while (startOfCodePoint > 0 && (rawName[startOfCodePoint] & 0b1100_0000) == 0b1000_0000) {
            startOfCodePoint--;
        }
        // Now verify that the code point between startOfCodePoint and maxBytes is valid
        if (Utf8.isWellFormed(rawName, startOfCodePoint, maxBytes - startOfCodePoint)) {
            // It is, so the valid length is maxBytes
            return maxBytes;
        }
        // Otherwise, the valid length is up to before the start of the code point
        return startOfCodePoint;
    }

    public static File assertInWindowsPathLengthLimitation(File file) {
        if (file.getAbsolutePath().length() > WINDOWS_PATH_LIMIT) {
            throw new GradleException(String.format("Cannot create file. '%s' exceeds windows path limitation of %d character.", file.getAbsolutePath(), WINDOWS_PATH_LIMIT));

        }
        return file;
    }
}
