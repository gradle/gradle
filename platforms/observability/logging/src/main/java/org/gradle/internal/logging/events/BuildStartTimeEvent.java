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

package org.gradle.internal.logging.events;

import org.gradle.api.logging.LogLevel;
import org.jspecify.annotations.Nullable;

/**
 * The start time of the build, which is also used for the build summary.
 * This event is sent only by the client to its own console, before the build request.
 */
public class BuildStartTimeEvent extends OutputEvent {

    private final long startTime;

    public BuildStartTimeEvent(long startTime) {
        this.startTime = startTime;
    }

    /**
     * The start time of the build, in milliseconds since the epoch.
     */
    public long getStartTime() {
        return startTime;
    }

    @Override
    public String toString() {
        return BuildStartTimeEvent.class.getSimpleName() + " " + startTime;
    }

    @Override
    public @Nullable LogLevel getLogLevel() {
        return null;
    }
}
