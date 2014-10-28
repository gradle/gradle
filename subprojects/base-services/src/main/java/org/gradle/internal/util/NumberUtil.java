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

package org.gradle.internal.util;

import static java.lang.String.format;

/**
 * Utility methods for working with numbers
 */
public class NumberUtil {

    /**
     * Percentage (0-...) of given input.
     * @param total the total, must be >= 0, if 0, the result will be 0.
     * @param fraction the fraction of total, must be >= 0. if 0, the result will be 100.
     */
    public static int percentOf(long total, long fraction) {
        if (total < 0 || fraction < 0) {
            throw new IllegalArgumentException("Unable to calculate percentage: " + fraction + " of " + total
                    + ". All inputs must be >= 0");
        }
        if (total == 0) {
            return 0;
        }
        float out = fraction * 100.0f / total;
        return (int) out;
    }

    /**
     * Formats bytes, e.g. 1000 -> 1kB, -2500 -> -2.5 kB
     */
    public static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "-".concat(formatBytes(-bytes));
        }
        int unit = 1000;
        if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        char pre = "kMGTPE".charAt(exp - 1);
        return format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }
}
