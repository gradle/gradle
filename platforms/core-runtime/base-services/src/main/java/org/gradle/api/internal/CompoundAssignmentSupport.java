/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal;

public class CompoundAssignmentSupport {
    public interface Freezable<T extends Freezable<T>> {
        T freeze();
    }

    @SuppressWarnings("unchecked")
    public static <T> T freeze(T object) {
        if (object instanceof Freezable) {
            return (T) ((Freezable<?>) object).freeze();
        }
        return object;
    }

    public static <T extends Freezable<T>> T freeze(T object) {
        return object.freeze();
    }

    public static byte freeze(byte v) {
        return v;
    }

    public static short freeze(short v) {
        return v;
    }

    public static char freeze(char v) {
        return v;
    }

    public static int freeze(int v) {
        return v;
    }

    public static long freeze(long v) {
        return v;
    }

    public static float freeze(float v) {
        return v;
    }

    public static double freeze(double v) {
        return v;
    }

    public static String freeze(String v) {
        return v;
    }
}
