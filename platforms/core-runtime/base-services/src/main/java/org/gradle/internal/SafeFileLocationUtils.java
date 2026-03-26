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
import com.google.common.collect.Streams;
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
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Sibling class to {@link FileUtils}, focused on obtaining safe file locations and names.
 */
public final class SafeFileLocationUtils {
    public static final int WINDOWS_PATH_LIMIT = 260;

    // SipHash-2-4 provides decent collision resistance of 64 bits while being fast to compute
    private static final HashFunction HASHER = Hashing.sipHash24();
    private static final BaseEncoding BASE_ENCODING = BaseEncoding.base32Hex().omitPadding();

    /**
     * The maximum file name length in bytes for most filesystems (e.g. ext4, NTFS).
     *
     * <p>
     * We use a smaller limit on input, but emit this size for outputs with hashes.
     * </p>
     */
    private static final int MAX_FILE_NAME_LENGTH_IN_BYTES = 120;

    /**
     * The maximum safe file name length in bytes to avoid exceeding filesystem limits after adding a hash suffix.
     *
     * <p>
     * We subtract 1 byte for the hyphen that will separate the original name from the hash suffix.
     * We divide the bits by 5 with round up as we encode them as base32 to keep it case-insensitive.
     * </p>
     */
    @VisibleForTesting
    static final int MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES = MAX_FILE_NAME_LENGTH_IN_BYTES - 1 - (HASHER.bits() + 4) / 5;

    /**
     * The character used to replace illegal characters in file names.
     */
    private static final CharBuffer ILLEGAL_CHAR_REPLACEMENT = CharBuffer.wrap("-");

    /**
     * Set of code points that are considered illegal in file names.
     * Stored as a sorted array to use binary search.
     * There is no int hash set and boxing every code point would likely be too inefficient.
     */
    private static final int[] INVALID_CODE_POINTS;
    static {
        INVALID_CODE_POINTS = Streams.concat(
            // Consider filesystem-illegal characters from all common OSes
            // Currently Windows is a superset of the others, but we include all for future-proofing
            IntStream.of(FileSystem.GENERIC.getIllegalFileNameCodePoints()),
            IntStream.of(FileSystem.LINUX.getIllegalFileNameCodePoints()),
            IntStream.of(FileSystem.MAC_OSX.getIllegalFileNameCodePoints()),
            IntStream.of(FileSystem.WINDOWS.getIllegalFileNameCodePoints()),
            // Drop whitespace characters that may cause problems in scripts or URLs
            // We should consider excluding all Unicode whitespace, but it's likely not as problematic
            IntStream.of(' ', '\t', '\n', '\r')
        ).distinct().sorted().toArray();
    }

    private static boolean isInvalidCodePoint(int codePoint) {
        int type = Character.getType(codePoint);
        // Reject invalid, not visible, or non-portable characters
        if (
            type == Character.CONTROL ||
                type == Character.PRIVATE_USE ||
                type == Character.SURROGATE ||
                type == Character.UNASSIGNED
        ) {
            return true;
        }
        // Check against our set of known invalid code points
        return Arrays.binarySearch(INVALID_CODE_POINTS, codePoint) >= 0;
    }

    // CharsetEncoder is not thread-safe, so use ThreadLocal to hold one per thread
    // These are not very expensive memory-wise, so this should be OK
    private static final ThreadLocal<CharsetEncoder> REPORTING_UTF_8_ENCODER =
        ThreadLocal.withInitial(() ->
            StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        );

    /**
     * Converts a string into a string that is safe to use as a file name.
     * The result will preserve Unicode characters while replacing filesystem-illegal
     * and web-problematic characters with "-".
     *
     * <p>
     * Long strings will also be hashed to avoid issues with file name length limitations.
     * </p>
     *
     * @param name the original name
     * @param forDirectory whether the name is intended for a directory
     * @return a safe file name derived from the original name
     */
    public static String toSafeFileName(String name, boolean forDirectory) {
        return toSafeFileName("", name, forDirectory);
    }

    /**
     * Converts a string into a string that is safe to use as a file name.
     * The result will preserve Unicode characters while replacing filesystem-illegal
     * and web-problematic characters with "-".
     *
     * <p>
     * Long strings will also be hashed to avoid issues with file name length limitations.
     * </p>
     *
     * @param prefix A prefix to add to the name that must be preserved (i.e. it cannot be truncated during file name shortening)
     * @param name the original name
     * @param forDirectory whether the name is intended for a directory
     * @return a safe file name derived from the original name
     */
    public static String toSafeFileName(String prefix, String name, boolean forDirectory) {
        Utf8EncodingResult nameResult;
        Utf8EncodingResult prefixResult;
        try {
            nameResult = encodeIntoUtf8WithReplacement(name);
            prefixResult = encodeIntoUtf8WithReplacement(prefix);
        } catch (CharacterCodingException e) {
            throw new AssertionError("Unexpected encoding error, should have filtered invalid input", e);
        }
        if (nameResult.cleanBytes.length + prefixResult.cleanBytes.length <= MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES) {
            return prefixResult.getCleanString() + removeInvalidStrings(forDirectory, nameResult.getCleanString());
        }

        // We use hashUnencodedChars to ensure we hash the original name without any replacements
        // This ensures that different original names that map to the same cleaned name still get different hashes
        byte[] hashBytes = HASHER.hashUnencodedChars(name).asBytes();
        String encoded = BASE_ENCODING.encode(hashBytes);

        String shortName = shortenNameAndAddHash(nameResult.cleanBytes, encoded, prefixResult.cleanBytes.length);
        return prefixResult.getCleanString() + removeInvalidStrings(forDirectory, shortName);
    }

    private static String removeInvalidStrings(boolean forDirectory, String name) {
        if (forDirectory) {
            // Remove any trailing dots for compatibility with win32 APIs
            int lastIndexOfNonDot = name.length() - 1;
            while (lastIndexOfNonDot >= 0 && name.charAt(lastIndexOfNonDot) == '.') {
                lastIndexOfNonDot--;
            }
            return name.substring(0, lastIndexOfNonDot + 1);
        }
        return name;
    }

    private static final class Utf8EncodingResult {
        private final String original;
        private final boolean hadIllegalChars;
        private final byte[] cleanBytes;

        public Utf8EncodingResult(String original, boolean hadIllegalChars, byte[] cleanBytes) {
            this.original = original;
            this.hadIllegalChars = hadIllegalChars;
            this.cleanBytes = cleanBytes;
        }

        public String getCleanString() {
            // Decode new string if we had to replace characters, otherwise return original name
            return hadIllegalChars ? new String(cleanBytes, StandardCharsets.UTF_8) : original;
        }
    }

    private static Utf8EncodingResult encodeIntoUtf8WithReplacement(String original) throws CharacterCodingException {
        // We use a reporting encoder for this operation to double-check that we do not have any malformed input
        // As we filter out invalid code points ourselves, we should never see an error from it.
        CharsetEncoder encoder = REPORTING_UTF_8_ENCODER.get().reset();
        ByteBuffer result = ByteBuffer.allocate((int) (original.length() * encoder.averageBytesPerChar()));
        boolean hadIllegalChars = false;
        int i = 0;
        while (i < original.length()) {
            int codePoint = original.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            boolean endOfInput = (i + charCount) >= original.length();

            if (isInvalidCodePoint(codePoint)) {
                hadIllegalChars = true;
                // Replace with illegal char replacement
                result = doEncode(encoder, ILLEGAL_CHAR_REPLACEMENT.duplicate(), result, endOfInput);
            } else {
                // Encode the single valid code point
                result = doEncode(encoder, CharBuffer.wrap(original, i, i + charCount), result, endOfInput);
            }

            i += charCount;
        }
        byte[] cleanBytes = Arrays.copyOf(result.array(), result.position());
        return new Utf8EncodingResult(original, hadIllegalChars, cleanBytes);
    }

    /**
     * Run one encoding step with the given encoder, char buffer and result byte buffer.
     * If the result buffer is too small, a larger one is allocated, encoded into, and returned.
     *
     * @param encoder the charset encoder
     * @param source the char buffer to encode from
     * @param result the byte buffer to encode into
     * @return the (possibly new) byte buffer containing the encoded bytes
     */
    private static ByteBuffer doEncode(CharsetEncoder encoder, CharBuffer source, ByteBuffer result, boolean endOfInput) throws CharacterCodingException {
        while (source.hasRemaining()) {
            CoderResult encodeResult = encoder.encode(source, result, endOfInput);
            if (encodeResult.isOverflow()) {
                result = reallocateBuffer(result);
            } else if (encodeResult.isUnderflow()) {
                // Done encoding
                break;
            } else {
                encodeResult.throwException();
            }
        }
        if (endOfInput) {
            // Flush the encoder
            while (true) {
                CoderResult flushResult = encoder.flush(result);
                if (flushResult.isOverflow()) {
                    result = reallocateBuffer(result);
                } else if (flushResult.isUnderflow()) {
                    // Done flushing
                    break;
                } else {
                    flushResult.throwException();
                }
            }
        }
        return result;
    }

    private static ByteBuffer reallocateBuffer(ByteBuffer result) {
        ByteBuffer newResult = ByteBuffer.allocate(result.capacity() * 2);
        result.flip();
        newResult.put(result);
        return newResult;
    }

    /**
     * Shorten the name to fit within {@code MAX_FILE_NAME_LENGTH_IN_BYTES} by truncating the original name
     * and appending a hyphen and the given encoded hash. If the original name has extension(s) shorter than
     * {@code MAX_FILE_NAME_LENGTH_IN_BYTES}, the extension(s) are preserved after the hash.
     *
     * @param rawName the original name in UTF-8 bytes
     * @param encoded the encoded hash string
     * @param lengthOfPrefix the number of bytes to reserve for a fixed prefix before the name
     * @return the shortened name with hash inserted
     */
    private static String shortenNameAndAddHash(byte[] rawName, String encoded, int lengthOfPrefix) {
        if (lengthOfPrefix > MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES) {
            throw new IllegalArgumentException("Prefix length exceeds maximum safe file name length");
        }

        ByteBuffer result = ByteBuffer.allocate(MAX_FILE_NAME_LENGTH_IN_BYTES);
        int maxLengthWithPrefix = MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - lengthOfPrefix;
        // Insert before any potential file extensions
        byte[] extensions = null;
        int safeLength;
        // Finds the position that indicates the start of extension segment(s) that can fit within the limit
        int firstDot = findStartOfExtension(rawName, lengthOfPrefix);
        if (firstDot > 0) {
            // If found, then it means we can preserve the extension either in its entirety or as a subset of the extension segments
            extensions = Arrays.copyOfRange(rawName, firstDot, rawName.length);
            int maxNameLengthWithPrefixAndExtensions = maxLengthWithPrefix - extensions.length;
            // Return the length of the name before the extension that still allows the selected extensions to fit
            safeLength = getSafeLength(rawName, maxNameLengthWithPrefixAndExtensions);
        } else {
            // Either there is no extension, or there is, but no segment can fit - just truncate the name in its entirety
            safeLength = getSafeLength(rawName, maxLengthWithPrefix);
        }

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
     * @param lengthOfPrefix the number of bytes to reserve for a fixed prefix before the name
     * @return the index of the first dot indicating the start of extension(s), or {@code -1} if none found
     */
    private static int findStartOfExtension(byte[] rawName, int lengthOfPrefix) {
        // We look for the first dot that is within (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - lengthOfPrefix - 1) from the end
        // This ensures that the extension can fit even after adding the hyphen, hash, and prefix
        // Also start at minimum 1 to avoid treating a leading dot as an extension
        int start = Math.max(1, rawName.length - (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - lengthOfPrefix - 1));
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
