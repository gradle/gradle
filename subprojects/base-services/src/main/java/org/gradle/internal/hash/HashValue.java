/*
 * Copyright 2011 the original author or authors.
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

import java.math.BigInteger;

public class HashValue {
    private final BigInteger digest;

    public HashValue(byte[] digest) {
        this.digest = new BigInteger(1, digest);
    }

    public HashValue(String hexString) {
        this.digest = new BigInteger(hexString, 16);
    }

    public static HashValue parse(String inputString) {
        if (inputString == null || inputString.length() == 0) {
            return null;
        }
        return new HashValue(parseInput(inputString));
    }

    private static String parseInput(String inputString) {
        if (inputString == null) {
            return null;
        }
        String cleaned = inputString.trim().toLowerCase();
        int spaceIndex = cleaned.indexOf(' ');
        if (spaceIndex != -1) {
            String firstPart = cleaned.substring(0, spaceIndex);
            if (firstPart.startsWith("md") || firstPart.startsWith("sha")) {
                cleaned = cleaned.substring(cleaned.lastIndexOf(' ') + 1);
            } else if (firstPart.endsWith(":")) {
                cleaned = cleaned.substring(spaceIndex + 1).replace(" ", "");
            } else {
                cleaned = cleaned.substring(0, spaceIndex);
            }
        }
        return cleaned;
    }

    public String asCompactString() {
        return digest.toString(36);
    }

    public String asHexString() {
        return digest.toString(16);
    }

    public byte[] asByteArray() {
        return digest.toByteArray();
    }

    public BigInteger asBigInteger() {
        return digest;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HashValue)) {
            return false;
        }

        HashValue otherHashValue = (HashValue) other;
        return digest.equals(otherHashValue.digest);
    }

    @Override
    public int hashCode() {
        return digest.hashCode();
    }
}
