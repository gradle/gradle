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

import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects durations recorded during this build and merges them into the store when the build tree stops.
 */
@ServiceScope(Scope.BuildTree.class)
public class DefaultTaskDurationHistory implements TaskDurationHistory, Stoppable {
    private final TaskDurationStore store;
    private final Map<Path, List<Duration>> recorded = new ConcurrentHashMap<>();

    public DefaultTaskDurationHistory(TaskDurationStore store) {
        this.store = store;
    }

    @Override
    @Nullable
    public TaskDurations getDurations(Path buildTreeTaskPath) {
        return store.read(buildTreeTaskPath);
    }

    public void record(Path buildTreeTaskPath, Duration duration) {
        recorded.compute(buildTreeTaskPath, (path, samples) -> {
            List<Duration> result = samples == null ? new ArrayList<>() : samples;
            result.add(duration);
            return result;
        });
    }

    @Override
    public void stop() {
        if (recorded.isEmpty()) {
            return;
        }
        Map<Path, TaskDurations> updated = new HashMap<>();
        recorded.forEach((path, samples) -> {
            TaskDurations durations = store.read(path);
            for (Duration sample : samples) {
                durations = durations == null ? TaskDurations.of(Collections.singletonList(sample)) : durations.withSample(sample);
            }
            updated.put(path, durations);
        });
        recorded.clear();
        store.write(updated);
    }
}
