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

package org.gradle.process.internal.health.memory;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.jvm.Jvm;

import java.util.Arrays;
import java.util.Locale;

/**
 * Helper to compute maximum heap sizes.
 */
public class MaximumHeapHelper {

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
     * @param osTotalMemory OS total memory in bytes
     * @return Default maximum heap size for the current JVM
     */
    public long getDefaultMaximumHeapSize(long osTotalMemory) {
        if (isIbmJvm()) {
            long totalMemoryHalf = osTotalMemory / 2;
            long halfGB = MemoryAmount.parseNotation("512m");
            return totalMemoryHalf > halfGB ? halfGB : totalMemoryHalf;
        }

        long totalMemoryFourth = osTotalMemory / 4;
        long oneGB = MemoryAmount.parseNotation("1g");
        switch (getJvmBitMode()) {
            case 32:
                return totalMemoryFourth > oneGB ? oneGB : totalMemoryFourth;
            case 64:
            default:
                if (isServerJvm()) {
                    long thirtyTwoGB = MemoryAmount.parseNotation("32g");
                    return totalMemoryFourth > thirtyTwoGB ? thirtyTwoGB : totalMemoryFourth;
                }
                return totalMemoryFourth > oneGB ? oneGB : totalMemoryFourth;
        }
    }

    @VisibleForTesting
    boolean isIbmJvm() {
        return Jvm.current().isIbmJvm();
    }

    @VisibleForTesting
    int getJvmBitMode() {
        for (String property : Arrays.asList("sun.arch.data.model", "com.ibm.vm.bitmode", "os.arch")) {
            if (System.getProperty(property, "").contains("64")) {
                return 64;
            }
        }
        return 32;
    }

    @VisibleForTesting
    boolean isServerJvm() {
        return !System.getProperty("java.vm.name").toLowerCase(Locale.US).contains("client");
    }
}
