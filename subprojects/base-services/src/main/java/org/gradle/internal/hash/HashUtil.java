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

import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class HashUtil {
    public static HashCode createHash(File file, HashFunction hashFunction) {
        try {
            FileInputStream input = new FileInputStream(file);
            try {
                return createHash(input, hashFunction);
            } finally {
                IOUtils.closeQuietly(input);
            }
        } catch (UncheckedIOException e) {
            // Catch any unchecked io exceptions and add the file path for troubleshooting
            throw new UncheckedIOException(String.format("Failed to create %s hash for file %s.", hashFunction, file.getAbsolutePath()), e.getCause());
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static HashCode createHash(InputStream input, HashFunction hashFunction) {
        Hasher hasher = hashFunction.newHasher();
        try {
            byte[] buffer = new byte[4096];
            while (true) {
                int nread = input.read(buffer);
                if (nread < 0) {
                    break;
                }
                hasher.putBytes(buffer, 0, nread);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return hasher.hash();
    }

    public static String createCompactMD5(String scriptText) {
        return Hashing.md5().hashString(scriptText).toCompactString();
    }

    public static HashCode sha1(byte[] bytes) {
        return Hashing.sha1().hashBytes(bytes);
    }

    public static HashCode sha1(InputStream inputStream) {
        return createHash(inputStream, Hashing.sha1());
    }

    public static HashCode sha1(File file) {
        return createHash(file, Hashing.sha1());
    }

    public static HashCode md5(InputStream inputStream) {
        return createHash(inputStream, Hashing.md5());
    }

    public static HashCode md5(File file) {
        return createHash(file, Hashing.md5());
    }

    public static HashCode sha256(byte[] bytes) {
        return Hashing.sha256().hashBytes(bytes);
    }

    public static HashCode sha256(InputStream inputStream) {
        return createHash(inputStream, Hashing.sha256());
    }

    public static HashCode sha256(File file) {
        return createHash(file, Hashing.sha256());
    }

}
