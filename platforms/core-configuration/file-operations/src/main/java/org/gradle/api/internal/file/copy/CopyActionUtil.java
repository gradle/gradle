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

import java.util.OptionalLong;

public class CopyActionUtil {

    /**
     * Computes the optional reproducible timestamp from the archive task properties.
     * <p>
     * An exception is thrown if the preserve file timestamps property is true and a reproducible file timestamp is specified.
     *
     * @param preserveFileTimestamps true to preserve file timestamps
     * @param reproducibleFileTimestamp the reproducible file timestamp property
     * @param minimumValue the minimum timestamp that can be specified
     * @return the optional reproducible timestamp computed based on the properties
     */
    public static OptionalLong computeReproducibleTimestamp(boolean preserveFileTimestamps, Provider<Long> reproducibleFileTimestamp, long minimumValue) {
        if (!reproducibleFileTimestamp.isPresent()) {
            return preserveFileTimestamps ? OptionalLong.empty() : OptionalLong.of(minimumValue);
        } else if (preserveFileTimestamps) {
            throw new InvalidUserDataException("The reproducible file timestamp property cannot be used when the preserve file timestamps property is set to true");
        } else {
            return OptionalLong.of(Math.max(reproducibleFileTimestamp.get(), minimumValue));
        }
    }

}
