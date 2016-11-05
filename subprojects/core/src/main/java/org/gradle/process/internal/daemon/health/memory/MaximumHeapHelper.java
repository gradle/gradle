/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal.daemon.health.memory;

import org.gradle.api.InvalidUserDataException;
import org.gradle.internal.jvm.Jvm;

import java.util.Arrays;
import java.util.Locale;

/**
 * Helper to compute maximum heap sizes.
 */
public class MaximumHeapHelper {
    private final MemoryInfo memoryInfo;

    public MaximumHeapHelper(MemoryInfo memoryInfo) {
        this.memoryInfo = memoryInfo;
    }

    /**
     * Parse heap size notation or return default maximum heap size if null or empty.
     *
     * @param heapSizeNotation Heap size notation, e.g. 512m or 4G.
     * @see #getDefaultMaximumHeapSize()
     */
    public long getMaximumHeapSize(String heapSizeNotation) {
        long parsed = parseHeapSize(heapSizeNotation);
        if (parsed != -1) {
            return parsed;
        }
        return getDefaultMaximumHeapSize();
    }

    /**
     * Get the default maximum heap.
     *
     * Different JVMs on different systems may use a different default for maximum heap when unset.
     * This method implements a best effort approximation, omitting rules for low memory systems (<192MB total RAM).
     *
     * See <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/vm/gctuning/parallel.html#default_heap_size">Oracle</a>
     * and <a href="http://www.ibm.com/support/knowledgecenter/SSYKE2_8.0.0/com.ibm.java.lnx.80.doc/diag/appendixes/defaults.html">IBM</a>
     * corresponding documentation.
     *
     * @return Default maximum heap size for the current JVM
     * @throws UnsupportedOperationException if the JVM doesn't support getting total physical memory.
     */
    public long getDefaultMaximumHeapSize() {
        long totalPhysicalMemory = memoryInfo.getTotalPhysicalMemory();

        if (Jvm.current().isIbmJvm()) {
            long totalMemoryHalf = totalPhysicalMemory / 2;
            long halfGB = parseHeapSize("512m");
            return totalMemoryHalf > halfGB ? halfGB : totalMemoryHalf;
        }

        long totalMemoryFourth = totalPhysicalMemory / 4;
        long oneGB = parseHeapSize("1g");
        switch (getJvmBitMode()) {
            case 32:
                return totalMemoryFourth > oneGB ? oneGB : totalMemoryFourth;
            case 64:
            default:
                if (isServerJvm()) {
                    long thirtyTwoGB = parseHeapSize("32g");
                    return totalMemoryFourth > thirtyTwoGB ? thirtyTwoGB : totalMemoryFourth;
                }
                return totalMemoryFourth > oneGB ? oneGB : totalMemoryFourth;
        }
    }

    private long parseHeapSize(String heapSizeNotation) {
        if (heapSizeNotation == null) {
            return -1;
        }
        String normalized = heapSizeNotation.toLowerCase(Locale.US).trim();
        if (normalized.isEmpty()) {
            return -1;
        }
        try {
            if (normalized.endsWith("m")) {
                return Long.parseLong(normalized.substring(0, normalized.length() - 1)) * 1024 * 1024;
            }
            if (normalized.endsWith("g")) {
                return Long.parseLong(normalized.substring(0, normalized.length() - 1)) * 1024 * 1024 * 1024;
            }
        } catch (NumberFormatException ex) {
            throw new InvalidUserDataException("Cannot parse heap size: " + heapSizeNotation, ex);
        }
        throw new InvalidUserDataException("Cannot parse heap size: " + heapSizeNotation);
    }

    private int getJvmBitMode() {
        for (String property : Arrays.asList("sun.arch.data.model", "com.ibm.vm.bitmode", "os.arch")) {
            String value = System.getProperty(property);
            if (value != null) {
                if (value.contains("64")) {
                    return 64;
                }
            }
        }
        return 32;
    }

    private boolean isServerJvm() {
        return !System.getProperty("java.vm.name").toLowerCase(Locale.US).contains("client");
    }
}
