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
 * Hasher abstraction that can be fed different kinds of primitives.
 * Inspired by the Google Guava project – https://github.com/google/guava.
 */
public interface Hasher {
    void putBytes(byte[] bytes);
    void putBytes(byte[] bytes, int off, int len);
    void putByte(byte value);
    void putInt(int value);
    void putLong(long value);
    void putDouble(double value);
    void putBoolean(boolean value);
    void putString(CharSequence value);
    void putHash(HashCode hashCode);
    HashCode hash();
}
