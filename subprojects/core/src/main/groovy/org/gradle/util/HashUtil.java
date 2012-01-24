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
package org.gradle.util;

import org.gradle.api.UncheckedIOException;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author Hans Dockter
 */
public class HashUtil {
    public static byte[] createHash(String scriptText, String algorithm) {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw UncheckedException.asUncheckedException(e);
        }
        messageDigest.update(scriptText.getBytes());
        return messageDigest.digest();
    }

    public static byte[] createHash(File file, String algorithm) {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw UncheckedException.asUncheckedException(e);
        }
        try {
            byte[] buffer = new byte[4096];
            InputStream instr = new FileInputStream(file);
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
        return messageDigest.digest();
    }

    public static String createHashString(String scriptText, String algorithm) {
        return byteToHexString(createHash(scriptText, algorithm));
    }

    public static String createHashString(File file, String algorithm) {
        return byteToHexString(createHash(file, algorithm));
    }

    public static String byteToHexString(byte[] digest) {
      StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            result.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }
      return result.toString();
    }

    public static String createCompactMD5(String scriptText) {
        return byteToCompactString(createHash(scriptText, "MD5"));
    }

    public static String byteToHexStringz(byte[] digest) {
        return new BigInteger(1, digest).toString(16);
    }

    private static String byteToCompactString(byte[] digest) {
        return new BigInteger(1, digest).toString(32);
    }
}
