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
package org.gradle.plugins.signing.signatory.pgp;

import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;

/**
 * A normalised form for keys, which are friendliest for users as hex strings but used internally as longs.
 */
public class PgpKeyId implements Comparable<PgpKeyId> {

    private final long asLong;
    private final String asHex;

    public PgpKeyId(long keyId) {
        asLong = keyId;
        asHex = toHex(keyId);
    }

    public PgpKeyId(PGPPublicKey keyId) {
        this(keyId.getKeyID());
    }

    public PgpKeyId(PGPSignature signature) {
        this(signature.getKeyID());
    }

    public PgpKeyId(String keyId) {
        asLong = toLong(keyId);
        asHex = toHex(asLong);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PgpKeyId
            && ((PgpKeyId) other).asHex.equals(this.asHex);
    }

    @Override
    public int hashCode() {
        return asHex.hashCode();
    }

    @Override
    public String toString() {
        return asHex;
    }

    @Override
    public int compareTo(PgpKeyId other) {
        return other == null
            ? -1
            : this.asHex.compareTo(other.asHex);
    }

    public final String getAsHex() {
        return asHex;
    }

    public final long getAsLong() {
        return asLong;
    }

    public static String toHex(long keyId) {
        return String.format("%08X", 0xFFFFFFFFL & keyId);
    }

    public static long toLong(String keyId) {
        if (keyId == null) {
            throw new IllegalArgumentException("'keyId' cannot be null");
        }

        String normalised = normaliseKeyId(keyId);
        try {
            return Long.parseLong(normalised, 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the key id \'" + keyId + "\' is not a valid hex string");
        }
    }

    private static String normaliseKeyId(String keyId) {
        String keyIdUpped = keyId.toUpperCase();
        switch (keyIdUpped.length()) {
            case 10:
                if (!keyIdUpped.startsWith("0X")) {
                    throw new IllegalArgumentException("10 character key IDs must start with 0x (given value: " + keyId + ")");
                }
                return keyIdUpped.substring(2);
            case 8:
                if (keyId.startsWith("0X")) {
                    throw new IllegalArgumentException("8 character key IDs must not start with 0x (given value: " + keyId + ")");
                }
                return keyIdUpped;
            default:
                throw new IllegalStateException("The key ID must be in a valid form (eg 00B5050F or 0x00B5050F), given value: " + keyId);
        }
    }
}
