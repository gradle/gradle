/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.api.internal.file.copy;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.provider.Provider;

import java.time.Instant;
import java.util.OptionalLong;
import java.util.function.LongUnaryOperator;

public class CopyActionUtil {

    /**
     * Computes the fixed timestamp for every archive entry, or empty to preserve each file's own timestamp.
     * <p>
     * When a timestamp is set it is clamped to the {@code [minimumValue, maximumValue]} range — values above the
     * maximum fail, values below the minimum are raised to it — and then adjusted by {@code timezoneNormalizer} for
     * how the format stores it. Clamping the literal value before normalizing keeps the result independent of the
     * build time zone.
     *
     * @param preserveFileTimestamps true to preserve file timestamps
     * @param reproducibleFileTimestamp the reproducible file timestamp property
     * @param timezoneNormalizer adjusts the timestamp for the time zone in which the format stores it (identity if none is needed)
     * @param minimumValue the minimum storable timestamp; smaller values are raised to it
     * @param maximumValue the maximum storable timestamp; larger values fail, use {@link Long#MAX_VALUE} for no maximum
     * @throws InvalidUserDataException if a timestamp is set together with preserveFileTimestamps, or if it exceeds maximumValue
     * @return the timestamp for every entry, or empty to preserve each file's timestamp
     */
    public static OptionalLong computeReproducibleTimestamp(boolean preserveFileTimestamps, Provider<Long> reproducibleFileTimestamp, LongUnaryOperator timezoneNormalizer, long minimumValue, long maximumValue) {
        if (!reproducibleFileTimestamp.isPresent()) {
            return preserveFileTimestamps ? OptionalLong.empty() : OptionalLong.of(timezoneNormalizer.applyAsLong(minimumValue));
        } else if (preserveFileTimestamps) {
            throw new InvalidUserDataException("The reproducible file timestamp property cannot be used when the preserve file timestamps property is set to true");
        } else {
            long timestamp = reproducibleFileTimestamp.get();
            if (timestamp > maximumValue) {
                throw new InvalidUserDataException(String.format(
                    "The reproducible file timestamp %s is greater than the maximum supported timestamp %s.",
                    Instant.ofEpochMilli(timestamp), Instant.ofEpochMilli(maximumValue)));
            }
            return OptionalLong.of(timezoneNormalizer.applyAsLong(Math.max(timestamp, minimumValue)));
        }
    }
}
