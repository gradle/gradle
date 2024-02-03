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

package org.gradle.util.internal;

import org.gradle.api.NonNullApi;

import javax.annotation.Nullable;

@NonNullApi
public class ArrayUtils {

    /**
     * Checks if a value is contained in the array.
     */
    @SuppressWarnings("manual_array_contains")
    public static <T> boolean contains(T[] array, @Nullable T value) {
        if (value == null) {
            for (T v : array) {
                if (v == null) {
                    return true;
                }
            }
        } else {
            for (T v : array) {
                if (value.equals(v)) {
                    return true;
                }
            }
        }

        return false;
    }
}
