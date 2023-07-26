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

import org.gradle.api.NonNullApi;

import java.util.ArrayList;
import java.util.List;

/**
 * A helper to parse {@code System.getProperty("java.version")}.
 */
@NonNullApi
public final class JavaVersionParser {
    private JavaVersionParser() {}

    /**
     * Converts the version string into a "major" number. Major is the first version component for modern Java version (9, 10, etc.).
     * For pre-9 versions that have version string starting with {@code "1."} (e.g. "1.8", "1.5") the second component is returned.
     * For example, parsing {@code "1.8"} yields 8.
     * <p>
     * This method recognizes modern "majors" in the legacy format too, so {@code "1.9"} yields 9.
     * <p>
     * Only versions starting from "1.1" are supported.
     *
     * @param version the version string to parse
     * @return the major as an integer
     */
    public static int getMajorFromVersionString(String version) {
        int firstNonVersionCharIndex = findFirstNonVersionCharIndex(version);

        String[] versionComponents = version.substring(0, firstNonVersionCharIndex).split("\\.");
        List<Integer> versions = convertToNumber(version, versionComponents);

        if (isLegacyVersion(versions)) {
            assertTrue(version, versions.get(1) > 0);
            return versions.get(1);
        } else {
            return versions.get(0);
        }
    }

    private static void assertTrue(String value, boolean condition) {
        if (!condition) {
            unparseable(value);
        }
    }

    private static boolean isLegacyVersion(List<Integer> versions) {
        return 1 == versions.get(0) && versions.size() > 1;
    }

    private static List<Integer> convertToNumber(String version, String[] versionComponents) {
        List<Integer> result = new ArrayList<Integer>();
        for (String s : versionComponents) {
            assertTrue(version, !isNumberStartingWithZero(s));
            try {
                result.add(Integer.parseInt(s));
            } catch (NumberFormatException e) {
                unparseable(version);
            }
        }
        assertTrue(version, !result.isEmpty() && result.get(0) > 0);
        return result;
    }

    private static boolean isNumberStartingWithZero(String number) {
        return number.length() > 1 && number.startsWith("0");
    }

    private static int findFirstNonVersionCharIndex(String s) {
        assertTrue(s, s.length() != 0);

        for (int i = 0; i < s.length(); ++i) {
            if (!isDigitOrPeriod(s.charAt(i))) {
                assertTrue(s, i != 0);
                return i;
            }
        }

        return s.length();
    }

    private static boolean isDigitOrPeriod(char c) {
        return (c >= '0' && c <= '9') || c == '.';
    }

    private static void unparseable(String version) {
        throw new IllegalArgumentException("Could not determine java version from '" + version + "'.");
    }
}
