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
 * Estimates how long work, such as the scheduled work of a build or a single task, will take to execute.
 */
public interface WorkDurationEstimator {

    /**
     * An estimator that never has timing data available.
     */
    WorkDurationEstimator UNAVAILABLE = event -> Optional.empty();

    /**
     * Returns the estimate for the work started by the given event, or empty when timing data is unavailable for any of it.
     */
    Optional<WorkEstimate> estimate(ProgressStartEvent event);
}
