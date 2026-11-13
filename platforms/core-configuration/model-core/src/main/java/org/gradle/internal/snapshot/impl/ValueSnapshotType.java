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

import org.gradle.internal.hash.Hasher;

/**
 * Identifies the type of a value snapshot in its hash, so that snapshots with the same content but different
 * types, which {@code equals()} tells apart, hash differently as well.
 */
enum ValueSnapshotType {
    NULL(1),
    BOOLEAN(2),
    INTEGER(3),
    LONG(4),
    SHORT(5),
    HASH_CODE(6),
    STRING(7),
    COERCING_STRING(8),
    FILE(9),
    ENUM(10),
    ATTRIBUTE(11),
    IMMUTABLE_MANAGED(12),
    MANAGED(13),
    JAVA_SERIALIZED(14),
    GRADLE_SERIALIZED(15),
    ARRAY_OF_PRIMITIVE(16);

    private final byte id;

    ValueSnapshotType(int id) {
        this.id = (byte) id;
    }

    void appendTo(Hasher hasher) {
        hasher.putByte(id);
    }
}
