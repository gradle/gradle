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

package org.gradle.api.internal.jvm;

import org.gradle.api.NonNullApi;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared code between {@link org.gradle.api.JavaVersion} and other code for parsing a full
 * Java version string.
 */
@NonNullApi
public class JavaVersionParser {

    public static int parseMajorVersion(String fullVersion) {
        int firstNonVersionCharIndex = findFirstNonVersionCharIndex(fullVersion);

        String[] versionStrings = fullVersion.substring(0, firstNonVersionCharIndex).split("\\.");
        List<Integer> versions = convertToNumber(fullVersion, versionStrings);

        if (isLegacyVersion(versions)) {
            assertTrue(fullVersion, versions.get(1) > 0);
            return versions.get(1);
        } else {
            return versions.get(0);
        }
    }

    private static void assertTrue(String value, boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException("Could not determine Java version from '" + value + "'.");
        }
    }

    private static boolean isLegacyVersion(List<Integer> versions) {
        return 1 == versions.get(0) && versions.size() > 1;
    }

    private static List<Integer> convertToNumber(String value, String[] versionStrs) {
        List<Integer> result = new ArrayList<Integer>();
        for (String s : versionStrs) {
            assertTrue(value, !isNumberStartingWithZero(s));
            try {
                result.add(Integer.parseInt(s));
            } catch (NumberFormatException e) {
                assertTrue(value, false);
            }
        }
        assertTrue(value, !result.isEmpty() && result.get(0) > 0);
        return result;
    }

    private static boolean isNumberStartingWithZero(String number) {
        return number.length() > 1 && number.startsWith("0");
    }

    private static int findFirstNonVersionCharIndex(String s) {
        assertTrue(s, !s.isEmpty());

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
}
