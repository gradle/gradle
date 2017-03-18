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

package org.gradle.api.internal.tasks.compile;

import com.google.common.collect.Sets;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.tasks.compile.BaseForkOptions;

import java.util.ArrayList;
import java.util.Set;

public class ForkOptionsMerger {

    /**
     * Merges two {@link BaseForkOptions} instances.
     *
     * Uses the largest {@link BaseForkOptions#getMemoryInitialSize()} and {@link BaseForkOptions#getMemoryMaximumSize()}.
     * Merges {@link BaseForkOptions#getJvmArgs()}, preserving order
     */
    public BaseForkOptions merge(BaseForkOptions left, BaseForkOptions right) {
        String mergedMinHeapSize = mergeHeapSize(left.getMemoryInitialSize(), right.getMemoryInitialSize());
        String mergedMaxHeapSize = mergeHeapSize(left.getMemoryMaximumSize(), right.getMemoryMaximumSize());
        Set<String> mergedJvmArgs = getNormalizedJvmArgs(left.getJvmArgs());
        mergedJvmArgs.addAll(getNormalizedJvmArgs(right.getJvmArgs()));

        BaseForkOptions merged = new BaseForkOptions();
        merged.setMemoryInitialSize(mergedMinHeapSize);
        merged.setMemoryMaximumSize(mergedMaxHeapSize);
        merged.setJvmArgs(new ArrayList<String>(mergedJvmArgs));
        return merged;
    }

    private String mergeHeapSize(String left, String right) {
        int mergedHeapSizeMb = Math.max(getHeapSizeMb(left), getHeapSizeMb(right));
        return mergedHeapSizeMb == -1 ? null : String.valueOf(mergedHeapSizeMb) + "m";
    }

    private int getHeapSizeMb(String heapSize) {
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

    private Set<String> getNormalizedJvmArgs(Iterable<String> jvmArgs) {
        Set<String> normalized = Sets.newLinkedHashSet();
        for (String jvmArg : jvmArgs) {
            normalized.add(jvmArg.trim());
        }
        return normalized;
    }
}
