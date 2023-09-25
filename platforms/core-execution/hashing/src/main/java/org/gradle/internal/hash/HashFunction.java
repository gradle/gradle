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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Hash function that can create new {@link Hasher}s and {@link PrimitiveHasher}s on demand.
 * Inspired by the Google Guava project – https://github.com/google/guava.
 */
public interface HashFunction {
    /**
     * Returns the algorithm used for this hashing function.
     */
    String getAlgorithm();

    /**
     * Returns a primitive hasher using the hash function.
     */
    PrimitiveHasher newPrimitiveHasher();

    /**
     * Returns a prefixing hasher using the hash function.
     */
    Hasher newHasher();

    /**
     * Hash the given bytes using the hash function.
     */
    HashCode hashBytes(byte[] bytes);

    /**
     * Hash the given string using the hash function.
     */
    HashCode hashString(CharSequence string);

    /**
     * Hash the contents of the given {@link java.io.InputStream}.
     */
    HashCode hashStream(InputStream stream) throws IOException;

    /**
     * Hash the contents of the given {@link java.io.File}.
     */
    HashCode hashFile(File file) throws IOException;

    /**
     * Returns the number of hexadecimal digits needed to represent the hash.
     */
    int getHexDigits();
}
