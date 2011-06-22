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
package org.gradle.plugins.signing.signatory.pgp

import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPSignature

/**
 * A normalised form for keys, which are friendliest for users as hex strings but used internally as longs.
 */
class PgpKeyId implements Comparable<PgpKeyId> {
    
    final String asHex
    final long asLong

    PgpKeyId(long keyId) {
        asLong = keyId
        asHex = toHex(keyId)
    }

    PgpKeyId(PGPPublicKey keyId) {
        this(keyId.keyID)
    }

    PgpKeyId(PGPSignature signature) {
        this(signature.keyID)
    }

    public PgpKeyId(String keyId) {
        asLong = toLong(keyId)
        asHex = toHex(asLong)
    }

    static String toHex(long keyId) {
        String.format("%08X", (0xFFFFFFFFL & keyId))
    }
    
    static long toLong(String keyId) {
        if (keyId == null) {
            throw new IllegalArgumentException("'keyId' cannot be null")
        }
        
        def normalised
        def keyIdUpped = keyId.toUpperCase()
        if (keyIdUpped.size() == 10) {
            if (!keyIdUpped.startsWith("0X")) {
                throw new IllegalArgumentException("10 character key IDs must start with 0x (given value: $keyId)")
            }

            normalised = keyIdUpped.substring(2)
        } else if (keyIdUpped.size() == 8) {
            if (keyId.startsWith("0X")) {
                throw new IllegalArgumentException("8 character key IDs must not start with 0x (given value: $keyId)")
            }
            
            normalised = keyIdUpped
        } else {
            throw new IllegalStateException("The key ID must be in a valid form (eg 00B5050F or 0x00B5050F), given value: $keyId")
        }
        
        try {
            Long.parseLong(normalised, 16)
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the key id '$keyId' is not a valid hex string")
        }
    }

    @Override
    boolean equals(Object other) {
        other instanceof PgpKeyId && other.asHex == this.asHex
    }

    @Override
    int hashCode() {
        asHex.hashCode()
    }

    @Override
    String toString() {
        asHex
    }

    int compareTo(PgpKeyId other) {
        other == null ? -1 : this.asHex.compareTo(other.asHex)
    }

}