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

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

import static java.math.RoundingMode.FLOOR;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

/**
 * Utility methods for working with numbers
 */
public class NumberUtil {
    public static final int BASE_LOG2 = 10;
    public static final int KIB_BASE = 1 << BASE_LOG2;
    private static final int FRACTIONAL_DIGIT_COUNT = 1;
    private static final MathContext MC = new MathContext(String.valueOf(KIB_BASE).length() + FRACTIONAL_DIGIT_COUNT, FLOOR);
    private static final List<String> UNITS = unmodifiableList(asList(" B", " KiB", " MiB", " GiB", " TiB", " PiB", " EiB"));
    private static final List<String> ORDINAL_SUFFIXES = unmodifiableList(asList("th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th"));


    /**
     * Percentage (0-...) of given input.
     *
     * @param fraction the fraction of total, must be &gt;= 0. if 0, the result will be 100.
     * @param total the total, must be &gt;= 0, if 0, the result will be 0.
     */
    public static int percentOf(long fraction, long total) {
        if (total < 0 || fraction < 0) {
            throw new IllegalArgumentException("Unable to calculate percentage: " + fraction + " of " + total + ". All inputs must be >= 0");
        }
        if (total == 0) {
            return 0;
        }
        float out = fraction * 100.0f / total;
        return (int) out;
    }

    /**
     * Formats bytes, e.g. 1010 -&gt; 1010 B, -1025 -&gt; -1 KiB, 1127 -&gt; 1.1 KiB
     */
    public static String formatBytes(@Nullable Long bytes) {
        if (bytes == null) {
            return "unknown size";
        } else if (bytes < 0) {
            return "-" + formatBytes(-bytes);
        } else {
            int baseExponent = (Long.SIZE - 1 - Long.numberOfLeadingZeros(bytes)) / BASE_LOG2;
            BigDecimal roundedBase = BigDecimal.valueOf(1L << (baseExponent * BASE_LOG2));
            BigDecimal result = BigDecimal.valueOf(bytes).divide(roundedBase, MC).setScale(FRACTIONAL_DIGIT_COUNT, FLOOR).stripTrailingZeros();
            return result.toPlainString() + UNITS.get(baseExponent);
        }
    }

    /**
     * gets ordinal String representation of given value (e.g. 1 -&gt; 1st, 12 -&gt; 12th, 22 -&gt; 22nd, etc.)
     */
    public static String ordinal(int value) {
        switch (value % 100) {
            case 11:
            case 12:
            case 13:
                return value + ORDINAL_SUFFIXES.get(0);
            default:
                return value + ORDINAL_SUFFIXES.get(value % 10);
        }
    }
}
