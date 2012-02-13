/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.daemon;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.tasks.compile.ForkOptions;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DaemonForkOptions {
    private final String minHeapSize;
    private final String maxHeapSize;
    private final List<String> jvmArgs;

    public DaemonForkOptions(String minHeapSize, String maxHeapSize, List<String> jvmArgs) {
        this.minHeapSize = minHeapSize;
        this.maxHeapSize = maxHeapSize;
        this.jvmArgs = jvmArgs;
    }
    
    public DaemonForkOptions(ForkOptions forkOptions) {
        this(forkOptions.getMemoryInitialSize(), forkOptions.getMemoryMaximumSize(), forkOptions.getJvmArgs());
    }

    public String getMinHeapSize() {
        return minHeapSize;
    }

    public String getMaxHeapSize() {
        return maxHeapSize;
    }

    public List<String> getJvmArgs() {
        return jvmArgs;
    }

    public boolean isCompatibleWith(DaemonForkOptions other) {
        return getHeapSizeMb(minHeapSize) >= getHeapSizeMb(other.getMinHeapSize())
                && getHeapSizeMb(maxHeapSize) >= getHeapSizeMb(other.getMaxHeapSize())
                && getNormalizedJvmArgs(jvmArgs).equals(getNormalizedJvmArgs(other.getJvmArgs()));
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
    
    private Set<String> getNormalizedJvmArgs(List<String> jvmArgs) {
        if (jvmArgs == null) {
            return Collections.emptySet();
        }

        Set<String> normalized = new HashSet<String>(jvmArgs.size());
        for (String jvmArg : jvmArgs) {
            normalized.add(jvmArg.trim());
        }
        return normalized;
    }
}
