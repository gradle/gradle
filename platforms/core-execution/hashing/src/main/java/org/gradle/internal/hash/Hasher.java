/*
 * Copyright 2017 the original author or authors.
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

/**
 * A safe hasher that can be marked as invalid.
 *
 * In order to avoid collisions we prepend the length of the next bytes to the underlying hasher (see this <a href="http://crypto.stackexchange.com/a/10065">answer</a> on stackexchange).
 */
public interface Hasher {
    /**
     * Feed a bunch of bytes into the hasher.
     */
    void putBytes(byte[] bytes);

    /**
     * Feed a given number of bytes into the hasher from the given offset.
     */
    void putBytes(byte[] bytes, int off, int len);

    /**
     * Feed a single byte into the hasher.
     */
    void putByte(byte value);

    /**
     * Feed an integer byte into the hasher.
     */
    void putInt(int value);

    /**
     * Feed a long value byte into the hasher.
     */
    void putLong(long value);

    /**
     * Feed a double value into the hasher.
     */
    void putDouble(double value);

    /**
     * Feed a boolean value into the hasher.
     */
    void putBoolean(boolean value);

    /**
     * Feed a string into the hasher.
     */
    void putString(CharSequence value);

    /**
     * Feed a hash code into the hasher.
     */
    void putHash(HashCode hashCode);

    /**
     * Puts a hashable value into the hasher.
     */
    void put(Hashable hashable);

    /**
     * Feed a {@code null} value into the hasher.
     */
    void putNull();

    /**
     * Returns the combined hash.
     *
     * If the build cache hash is invalid, an exception is thrown.
     *
     * @throws IllegalStateException if the hasher state is invalid.
     */
    HashCode hash();
}
