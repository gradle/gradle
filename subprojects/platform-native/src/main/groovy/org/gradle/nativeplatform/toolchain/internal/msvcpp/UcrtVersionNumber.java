/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import java.util.Arrays;

public class UcrtVersionNumber implements Comparable<UcrtVersionNumber> {
    private int[] values;

    public UcrtVersionNumber(int... values) {
        this.values = values;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UcrtVersionNumber that = (UcrtVersionNumber) o;

        return Arrays.equals(values, that.values);
    }

    @Override
    public String toString() {
        return toVersionString(values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    public static UcrtVersionNumber parse(String version) {
        return new UcrtVersionNumber(parseVersionString(version));
    }

    @Override
    public int compareTo(UcrtVersionNumber o) {
        if (Arrays.equals(values, o.values)) {
           return 0;
        }
        int len = Math.min(values.length, o.values.length);
        for (int i = 0; i < len; ++i) {
            int ret = values[i] - o.values[i];
            if (ret < 0) {
                return -1;
            } else if (ret > 0) {
                return 1;
            }
        }
        return values.length < o.values.length ? -1 : 1;
    }

    private static int[] parseVersionString(String version) {
        String[] parts = version.split("\\.");
        int[] values = new int[parts.length];
        for (int i = 0; i < parts.length; ++i) {
            values[i] = Integer.valueOf(parts[i]);
        }
        return values;
    }

    private static String toVersionString(int... values) {
        StringBuilder sb = new StringBuilder();
        boolean firstValue = true;
        for (int value : values) {
            if (!firstValue) {
                sb.append('.');
            } else {
                firstValue = false;
            }
            sb.append(value);
        }
        return sb.toString();
    }
}
