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

package org.gradle.api.internal.tasks.execution.durations;

import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

/**
 * The most recent successful execution durations of a task, oldest first.
 */
public final class TaskDurations {
    public static final int MAX_SAMPLES = 3;

    private final ImmutableList<Duration> recent;

    private TaskDurations(ImmutableList<Duration> recent) {
        this.recent = recent;
    }

    /**
     * Creates an instance from the given samples, oldest first, keeping only the most recent {@link #MAX_SAMPLES}.
     */
    public static TaskDurations of(List<Duration> samples) {
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("At least one duration is required.");
        }
        int from = Math.max(0, samples.size() - MAX_SAMPLES);
        return new TaskDurations(ImmutableList.copyOf(samples.subList(from, samples.size())));
    }

    public List<Duration> getRecent() {
        return recent;
    }

    /**
     * The mean of the recent durations, truncated to milliseconds.
     */
    public Duration getAverage() {
        long totalMillis = 0;
        for (Duration duration : recent) {
            totalMillis += duration.toMillis();
        }
        return Duration.ofMillis(totalMillis / recent.size());
    }

    public TaskDurations withSample(Duration sample) {
        return of(ImmutableList.<Duration>builder().addAll(recent).add(sample).build());
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof TaskDurations && recent.equals(((TaskDurations) o).recent);
    }

    @Override
    public int hashCode() {
        return recent.hashCode();
    }

    @Override
    public String toString() {
        return "TaskDurations" + recent;
    }
}
