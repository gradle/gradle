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

package org.gradle.internal.logging.events;

import org.gradle.api.logging.LogLevel;

/**
 * Indicates an event that can trigger an immediate update to the console.
 */
public class UpdateNowEvent extends OutputEvent {

    private final long timestamp;

    public UpdateNowEvent(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return UpdateNowEvent.class.getSimpleName() + " " + timestamp;
    }

    @Override
    public LogLevel getLogLevel() {
        return null;
    }
}
