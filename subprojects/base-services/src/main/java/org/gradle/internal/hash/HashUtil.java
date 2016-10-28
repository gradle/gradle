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
package org.gradle.internal.hash;

import com.google.common.hash.HashCode;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.UncheckedException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtil {
    public static HashValue createHash(String scriptText, String algorithm) {
        MessageDigest messageDigest = createMessageDigest(algorithm);
        messageDigest.update(scriptText.getBytes());
        return new HashValue(messageDigest.digest());
    }

    public static HashValue createHash(File file, String algorithm) {
        try {
            return createHash(new FileInputStream(file), algorithm);
        } catch (UncheckedIOException e) {
            // Catch any unchecked io exceptions and add the file path for troubleshooting
            throw new UncheckedIOException(String.format("Failed to create %s hash for file %s.", algorithm, file.getAbsolutePath()), e.getCause());
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static HashValue createHash(InputStream instr, String algorithm) {
        MessageDigest messageDigest;
        try {
            messageDigest = createMessageDigest(algorithm);
            byte[] buffer = new byte[4096];
            try {
                while (true) {
                    int nread = instr.read(buffer);
                    if (nread < 0) {
                        break;
                    }
                    messageDigest.update(buffer, 0, nread);
                }
            } finally {
                instr.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new HashValue(messageDigest.digest());
    }

    private static MessageDigest createMessageDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public static String createCompactMD5(String scriptText) {
        return createHash(scriptText, "MD5").asCompactString();
    }

    public static String compactStringFor(HashCode hashCode) {
        return compactStringFor(hashCode.asBytes());
    }

    public static String compactStringFor(byte[] digest) {
        return new HashValue(digest).asCompactString();
    }

    public static HashValue sha1(byte[] bytes) {
        return createHash(new ByteArrayInputStream(bytes), "SHA1");
    }

    public static HashValue sha1(InputStream inputStream) {
        return createHash(inputStream, "SHA1");
    }

    public static HashValue sha1(File file) {
        return createHash(file, "SHA1");
    }

    public static HashValue sha256(byte[] bytes) {
        return createHash(new ByteArrayInputStream(bytes), "SHA-256");
    }

    public static HashValue sha256(InputStream inputStream) {
        return createHash(inputStream, "SHA-256");
    }

    public static HashValue sha256(File file) {
        return createHash(file, "SHA-256");
    }

    public static int compareHashCodes(HashCode a, HashCode b) {
        return compareHashCodes(a.asBytes(), b.asBytes());
    }

    public static int compareHashCodes(byte[] a, byte[] b) {
        int result;
        int len = a.length;
        result = len - b.length;
        if (result == 0) {
            for (int idx = 0; idx < len; idx++) {
                result = a[idx] - b[idx];
                if (result != 0) {
                    break;
                }
            }
        }
        return result;
    }
}
