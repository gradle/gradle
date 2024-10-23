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
public class DecoderExtensions {

    public static short[] readLengthPrefixedShorts(Decoder decoder) throws IOException {
        int length = decoder.readInt();
        short[] array = new short[length];
        readShorts(decoder, array);
        return array;
    }

    public static void readShorts(Decoder decoder, short[] array) throws IOException {
        for (int i = 0; i < array.length; i++) {
            array[i] = decoder.readShort();
        }
    }

    public static int[] readLengthPrefixedInts(Decoder decoder) throws IOException {
        int length = decoder.readInt();
        int[] array = new int[length];
        readInts(decoder, array);
        return array;
    }

    public static void readInts(Decoder decoder, int[] array) throws IOException {
        for (int i = 0; i < array.length; i++) {
            array[i] = decoder.readInt();
        }
    }

    public static long[] readLengthPrefixedLongs(Decoder decoder) throws IOException {
        int length = decoder.readInt();
        long[] array = new long[length];
        readLongs(decoder, array);
        return array;
    }

    public static void readLongs(Decoder decoder, long[] array) throws IOException {
        for (int i = 0; i < array.length; i++) {
            array[i] = decoder.readLong();
        }
    }

    public static float[] readLengthPrefixedFloats(Decoder decoder) throws IOException {
        int length = decoder.readInt();
        float[] array = new float[length];
        readFloats(decoder, array);
        return array;
    }

    public static void readFloats(Decoder decoder, float[] array) throws IOException {
        for (int i = 0; i < array.length; i++) {
            array[i] = decoder.readFloat();
        }
    }

    public static double[] readLengthPrefixedDoubles(Decoder decoder) throws IOException {
        int length = decoder.readInt();
        double[] array = new double[length];
        readDoubles(decoder, array);
        return array;
    }

    public static void readDoubles(Decoder decoder, double[] array) throws IOException {
        for (int i = 0; i < array.length; i++) {
            array[i] = decoder.readDouble();
        }
    }

    public static char[] readLengthPrefixedChars(Decoder decoder) throws IOException {
        int length = decoder.readInt();
        char[] array = new char[length];
        readChars(decoder, array);
        return array;
    }

    public static void readChars(Decoder decoder, char[] array) throws IOException {
        for (int i = 0; i < array.length; i++) {
            array[i] = (char) decoder.readInt();
        }
    }

    public static boolean[] readLengthPrefixedBooleans(Decoder decoder) throws IOException {
        int length = decoder.readInt();
        boolean[] array = new boolean[length];
        readBooleans(decoder, array);
        return array;
    }

    public static void readBooleans(Decoder decoder, boolean[] array) throws IOException {
        for (int i = 0; i < array.length; i++) {
            array[i] = decoder.readBoolean();
        }
    }
}
