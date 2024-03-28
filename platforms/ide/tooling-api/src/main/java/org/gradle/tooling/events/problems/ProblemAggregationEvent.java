/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.tooling.events.problems;

import org.gradle.api.Incubating;


/**
 * Represents a list of aggregated problems. These are sent at the end of the build.
 * All Problems that occurred more than once during the build are aggregated and sent as a {@link ProblemAggregation}.
 * They won't be sent in between the build only the first one.
 *
 * @since 8.8
 */
@Incubating
public interface ProblemAggregationEvent extends ProblemEvent {

    /**
     * Returns a problem aggregation.
     *
     * @return a problem aggregation
     * @since 8.8
     */
    ProblemAggregation getProblemAggregation();
}
