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
public interface Hasher extends PrimitiveHasher {
    /**
     * Feed a {@code null} value into the hasher.
     */
    void putNull();

    /**
     * Marks this hash code as invalid. Further values fed into the hasher will be ignored,
     * {@link #isValid()} will return {@code false}, and {@link #hash()} will throw an exception.
     */
    void markAsInvalid(String invalidReason);

    /**
     * Whether the build cache hash is valid.
     */
    boolean isValid();

    /**
     * Reason why the hash is not valid.
     */
    String getInvalidReason();
}
