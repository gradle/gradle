/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.snapshot.impl;

import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.jspecify.annotations.Nullable;

final class SnapshotHashing {
    private SnapshotHashing() {
    }

    /**
     * Hashes the UTF-16 code units. {@link Hasher#putString(CharSequence)} encodes as UTF-8, which maps every
     * unpaired surrogate to the same replacement and so cannot tell such strings apart.
     */
    static void putString(Hasher hasher, String value) {
        byte[] codeUnits = new byte[value.length() * 2];
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            codeUnits[2 * i] = (byte) (c >> 8);
            codeUnits[2 * i + 1] = (byte) c;
        }
        hasher.putBytes(codeUnits);
    }

    /**
     * A null marker alone is not enough: {@link Hasher#putNull()} produces the same bytes as some short values.
     */
    static void putNullableString(Hasher hasher, @Nullable String value) {
        hasher.putBoolean(value != null);
        if (value != null) {
            putString(hasher, value);
        }
    }

    static void putNullableHash(Hasher hasher, @Nullable HashCode value) {
        hasher.putBoolean(value != null);
        if (value != null) {
            hasher.putHash(value);
        }
    }
}
