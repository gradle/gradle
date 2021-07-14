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

package org.gradle.process.internal.util;

import com.google.common.collect.Sets;
import org.gradle.api.InvalidUserDataException;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Strings.nullToEmpty;

public class MergeOptionsUtil {
    public static int getHeapSizeMb(String heapSize) {
        if (heapSize == null) {
            return -1; // unspecified
        }

        String normalized = heapSize.trim().toLowerCase();
        try {
            if (normalized.endsWith("m")) {
                return Integer.parseInt(normalized.substring(0, normalized.length() - 1));
            }
            if (normalized.endsWith("g")) {
                return Integer.parseInt(normalized.substring(0, normalized.length() - 1)) * 1024;
            }
        } catch (NumberFormatException e) {
            throw new InvalidUserDataException("Cannot parse heap size: " + heapSize, e);
        }
        throw new InvalidUserDataException("Cannot parse heap size: " + heapSize);
    }

    public static String mergeHeapSize(String heapSize1, String heapSize2) {
        int mergedHeapSizeMb = Math.max(getHeapSizeMb(heapSize1), getHeapSizeMb(heapSize2));
        return mergedHeapSizeMb == -1 ? null : String.valueOf(mergedHeapSizeMb) + "m";
    }

    public static boolean canBeMerged(String left, String right) {
        if (left == null || right == null) {
            return true;
        } else {
            return normalized(left).equals(normalized(right));
        }
    }

    public static boolean canBeMerged(File left, File right) {
        if (left == null || right == null) {
            return true;
        } else {
            return left.equals(right);
        }
    }

    public static Set<String> normalized(@Nullable Iterable<String> strings) {
        Set<String> normalized = Sets.newLinkedHashSet();
        if (strings != null) {
            for (String string : strings) {
                normalized.add(normalized(string));
            }
        }
        return normalized;
    }

    public static String normalized(@Nullable String string) {
        return nullToEmpty(string).trim();
    }

    public static boolean containsAll(Map<String, Object> left, Map<String, Object> right) {
        for (String rightKey : right.keySet()) {
            if (!normalized(left.keySet()).contains(normalized(rightKey))) {
                return false;
            } else {
                for (String leftKey : left.keySet()) {
                    if (normalized(leftKey).equals(normalized(rightKey)) && !left.get(leftKey).equals(right.get(rightKey))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
