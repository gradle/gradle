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
package org.gradle.reporting;

import java.math.BigDecimal;

/**
 * This internal detail is used by the Android plugin < 3.0. It will be removed in Gradle 5.0.
 */
@Deprecated
public class DurationFormatter {
    public static final int MILLIS_PER_SECOND = 1000;
    public static final int MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND;
    public static final int MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE;
    public static final int MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR;

    public String format(long duration) {
        if (duration == 0) {
            return "0s";
        }

        StringBuilder result = new StringBuilder();

        long days = duration / MILLIS_PER_DAY;
        duration = duration % MILLIS_PER_DAY;
        if (days > 0) {
            result.append(days);
            result.append("d");
        }
        long hours = duration / MILLIS_PER_HOUR;
        duration = duration % MILLIS_PER_HOUR;
        if (hours > 0 || result.length() > 0) {
            result.append(hours);
            result.append("h");
        }
        long minutes = duration / MILLIS_PER_MINUTE;
        duration = duration % MILLIS_PER_MINUTE;
        if (minutes > 0 || result.length() > 0) {
            result.append(minutes);
            result.append("m");
        }
        int secondsScale = result.length() > 0 ? 2 : 3;
        result.append(BigDecimal.valueOf(duration).divide(BigDecimal.valueOf(MILLIS_PER_SECOND)).setScale(secondsScale, BigDecimal.ROUND_HALF_UP));
        result.append("s");
        return result.toString();
    }
}
