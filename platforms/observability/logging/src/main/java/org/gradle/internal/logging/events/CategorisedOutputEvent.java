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

package org.gradle.internal.logging.events;

import org.gradle.api.logging.LogLevel;

public class CategorisedOutputEvent extends OutputEvent {
    private final String category;
    private final LogLevel logLevel;
    private final long timestamp;

    public CategorisedOutputEvent(long timestamp, String category, LogLevel logLevel) {
        this.timestamp = timestamp;
        this.category = category;
        this.logLevel = logLevel;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public LogLevel getLogLevel() {
        return logLevel;
    }

    public String getCategory() {
        return category;
    }
}
