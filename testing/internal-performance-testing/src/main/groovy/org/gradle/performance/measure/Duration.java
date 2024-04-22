/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.performance.measure;

import java.math.BigDecimal;

public class Duration {
    public static final Units<Duration> MILLI_SECONDS = Units.base(Duration.class, "ms");
    public static final Units<Duration> SECONDS = MILLI_SECONDS.times(1000, "s");
    public static final Units<Duration> MINUTES = SECONDS.times(60, "m");
    public static final Units<Duration> HOURS = MINUTES.times(60, "h");

    public static Amount<Duration> millis(long millis) {
        return Amount.valueOf(millis, MILLI_SECONDS);
    }

    public static Amount<Duration> millis(BigDecimal millis) {
        return Amount.valueOf(millis, MILLI_SECONDS);
    }

    public static Amount<Duration> seconds(BigDecimal seconds) {
        return Amount.valueOf(seconds, SECONDS);
    }

    public static Amount<Duration> minutes(BigDecimal minutes) {
        return Amount.valueOf(minutes, MINUTES);
    }

    public static Amount<Duration> hours(BigDecimal hours) {
        return Amount.valueOf(hours, HOURS);
    }
}
