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

package org.gradle.internal.logging.console;

import org.gradle.internal.logging.events.ProgressStartEvent;

import java.util.Optional;

/**
 * Uses the estimates the build attaches to the events that start work, based on the recorded durations of previous executions.
 */
public class RecordedWorkDurationEstimator implements WorkDurationEstimator {

    /**
     * System property that disables estimates when set to {@code false}.
     */
    public static final String ENABLED_PROPERTY = "org.gradle.internal.console.work-duration-estimates";

    public static WorkDurationEstimator createFromSystemProperties() {
        return Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"))
            ? new RecordedWorkDurationEstimator()
            : WorkDurationEstimator.UNAVAILABLE;
    }

    @Override
    public Optional<WorkEstimate> estimate(ProgressStartEvent event) {
        Long durationMillis = event.getEstimatedDurationMillis();
        Long criticalPathMillis = event.getEstimatedCriticalPathMillis();
        if (durationMillis == null || criticalPathMillis == null) {
            return Optional.empty();
        }
        return Optional.of(new WorkEstimate(durationMillis, criticalPathMillis));
    }
}
