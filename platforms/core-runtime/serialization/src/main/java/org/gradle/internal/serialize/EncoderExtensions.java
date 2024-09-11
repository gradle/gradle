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

package org.gradle.internal.serialize;

import org.gradle.api.NonNullApi;

import java.io.IOException;

@NonNullApi
public class EncoderExtensions {
    public static void writeLengthPrefixedShorts(Encoder encoder, short[] array) throws IOException {
        encoder.writeInt(array.length);
        writeShorts(encoder, array);
    }

    public static void writeShorts(Encoder encoder, short[] array) throws IOException {
        for (short e : array) {
            encoder.writeShort(e);
        }
    }

    public static void writeLengthPrefixedInts(Encoder encoder, int[] array) throws IOException {
        encoder.writeInt(array.length);
        writeInts(encoder, array);
    }

    public static void writeInts(Encoder encoder, int[] array) throws IOException {
        for (int e : array) {
            encoder.writeInt(e);
        }
    }

    public static void writeLengthPrefixedLongs(Encoder encoder, long[] array) throws IOException {
        encoder.writeInt(array.length);
        writeLongs(encoder, array);
    }

    public static void writeLongs(Encoder encoder, long[] array) throws IOException {
        for (long e : array) {
            encoder.writeLong(e);
        }
    }

    public static void writeLengthPrefixedFloats(Encoder encoder, float[] array) throws IOException {
        encoder.writeInt(array.length);
        writeFloats(encoder, array);
    }

    public static void writeFloats(Encoder encoder, float[] array) throws IOException {
        for (float e : array) {
            encoder.writeFloat(e);
        }
    }

    public static void writeLengthPrefixedDoubles(Encoder encoder, double[] array) throws IOException {
        encoder.writeInt(array.length);
        writeDoubles(encoder, array);
    }

    public static void writeDoubles(Encoder encoder, double[] array) throws IOException {
        for (double e : array) {
            encoder.writeDouble(e);
        }
    }

    public static void writeLengthPrefixedChars(Encoder encoder, char[] array) throws IOException {
        encoder.writeInt(array.length);
        writeChars(encoder, array);
    }

    public static void writeChars(Encoder encoder, char[] array) throws IOException {
        for (char e : array) {
            encoder.writeInt(e);
        }
    }

    public static void writeLengthPrefixedBooleans(Encoder encoder, boolean[] array) throws IOException {
        encoder.writeInt(array.length);
        writeBooleans(encoder, array);
    }

    private static void writeBooleans(Encoder encoder, boolean[] array) throws IOException {
        for (boolean e : array) {
            encoder.writeBoolean(e);
        }
    }
}
