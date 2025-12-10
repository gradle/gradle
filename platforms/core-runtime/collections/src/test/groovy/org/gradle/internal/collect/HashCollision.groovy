/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.collect

/**
 * Helper class for testing hash collision scenarios.
 * Each instance has a controllable hash code but unique identity.
 */
class HashCollision {
    private static int nextId = 0

    final int hash
    final int id  // Unique identifier to distinguish instances with same hash

    HashCollision(int hash) {
        this.hash = hash
        this.id = nextId++
    }

    @Override
    int hashCode() {
        hash
    }

    @Override
    boolean equals(Object other) {
        if (other === this) return true
        if (!(other instanceof HashCollision)) return false
        // Two HashCollision instances are equal only if they have the same id
        // This ensures we can have multiple distinct objects with the same hash
        return this.id == ((HashCollision) other).id
    }

    @Override
    String toString() {
        "Collision($hash#$id)"
    }
}
