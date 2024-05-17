/*
 * Copyright 2024 the original author or authors.
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
 * Additional methods for building hashes via the {@link Hasher} interface.
 */
public class HasherExtensions {
    public static void putBooleans(Hasher hasher, boolean[] array) {
        hasher.putInt(array.length);
        for (boolean b : array) {
            hasher.putBoolean(b);
        }
    }

    public static void putChars(Hasher hasher, char[] array) {
        hasher.putInt(array.length);
        for (char c : array) {
            hasher.putInt(c);
        }
    }

    public static void putDoubles(Hasher hasher, double[] array) {
        hasher.putInt(array.length);
        for (double d : array) {
            hasher.putDouble(d);
        }
    }

    public static void putFloats(Hasher hasher, float[] array) {
        hasher.putInt(array.length);
        for (float f : array) {
            hasher.putInt(Float.floatToRawIntBits(f));
        }
    }

    public static void putLongs(Hasher hasher, long[] array) {
        hasher.putInt(array.length);
        for (long l : array) {
            hasher.putLong(l);
        }
    }

    public static void putInts(Hasher hasher, int[] array) {
        hasher.putInt(array.length);
        for (int i : array) {
            hasher.putInt(i);
        }
    }

    public static void putShorts(Hasher hasher, short[] array) {
        hasher.putInt(array.length);
        for (short s : array) {
            hasher.putInt(s);
        }
    }
}
