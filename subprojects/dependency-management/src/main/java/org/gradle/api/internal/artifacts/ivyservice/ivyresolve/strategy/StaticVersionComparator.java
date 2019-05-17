/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy;

import com.google.common.collect.ImmutableMap;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

/**
 * Allows for comparison of Version instances.
 * Note that this comparator only considers the 'parts' of a version, and does not consider the part 'separators'.
 * This means that it considers `1.1.1 == 1-1-1 == 1.1-1`, and should not be used in cases where this is important.
 * One example where this comparator is inappropriate is if versions should be retained in a TreeMap/TreeSet.
 */
class StaticVersionComparator implements Comparator<Version> {
    private static final Map<String, Integer> SPECIAL_MEANINGS =
            ImmutableMap.of("dev", -1, "rc", 1, "release", 2, "final", 3);

    /**
     * Compares 2 versions. Algorithm is inspired by PHP version_compare one.
     */
    @Override
    public int compare(Version version1, Version version2) {
        if (version1.equals(version2)) {
            return 0;
        }

        String[] parts1 = version1.getParts();
        String[] parts2 = version2.getParts();
        Long[] numericParts1 = version1.getNumericParts();
        Long[] numericParts2 = version2.getNumericParts();

        int i = 0;
        for (; i < parts1.length && i < parts2.length; i++) {
            String part1 = parts1[i];
            String part2 = parts2[i];

            Long numericPart1 = numericParts1[i];
            Long numericPart2 = numericParts2[i];

            boolean is1Number = numericPart1 != null;
            boolean is2Number = numericPart2 != null;

            if (part1.equals(part2)) {
                continue;
            }
            if (is1Number && !is2Number) {
                return 1;
            }
            if (is2Number && !is1Number) {
                return -1;
            }
            if (is1Number && is2Number) {
                int result = numericPart1.compareTo(numericPart2);
                if (result == 0) {
                    continue;
                }
                return result;
            }
            // both are strings, we compare them taking into account special meaning
            Integer sm1 = SPECIAL_MEANINGS.get(part1.toLowerCase(Locale.US));
            Integer sm2 = SPECIAL_MEANINGS.get(part2.toLowerCase(Locale.US));
            if (sm1 != null) {
                sm2 = sm2 == null ? 0 : sm2;
                return sm1 - sm2;
            }
            if (sm2 != null) {
                return -sm2;
            }
            return part1.compareTo(part2);
        }
        if (i < parts1.length) {
            return numericParts1[i] == null ? -1 : 1;
        }
        if (i < parts2.length) {
            return numericParts2[i] == null ? 1 : -1;
        }

        return 0;
    }
}
