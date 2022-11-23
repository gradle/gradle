/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.cache;

import org.gradle.api.Incubating;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A supplier of timestamps for the purposes of calculating expiration dates
 * on cache entries.
 *
 * @since 8.0
 */
@Incubating
public interface TimestampSupplier extends Supplier<Long> {
    /**
     * Returns a timestamp supplier that calculates a timestamp exactly the given number
     * of days prior to the current time, or 0 if the number of days extends beyond the
     * epoch.
     */
    static TimestampSupplier olderThanInDays(int days) {
        // This needs to be an anonymous inner class instead of a lambda for configuration cache compatibility
        return new TimestampSupplier() {
            @Override
            public Long get() {return Math.max(0, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days));}
        };
    }
}
