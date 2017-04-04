/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.logging.format;

import java.util.concurrent.TimeUnit;

public class TersePrettyDurationFormatter implements DurationFormatter {
    private static final long MS_PER_MINUTE = TimeUnit.MINUTES.toMillis(1);
    private static final long MS_PER_HOUR = TimeUnit.HOURS.toMillis(1);

    @Override
    public String format(long elapsedTimeInMs) {
        StringBuilder result = new StringBuilder();
        if (elapsedTimeInMs > MS_PER_HOUR) {
            result.append(elapsedTimeInMs / MS_PER_HOUR).append("h ");
        }
        if (elapsedTimeInMs > MS_PER_MINUTE) {
            result.append((elapsedTimeInMs % MS_PER_HOUR) / MS_PER_MINUTE).append("m ");
        }
        result.append((elapsedTimeInMs % MS_PER_MINUTE) / 1000).append("s");
        return result.toString();
    }
}
