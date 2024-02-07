/*
 * Copyright 2023 the original author or authors.
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

import java.util.List;

/**
 * Describes the problem aggregations sent at the end of the build.
 *
 * @since 8.6
 */
@Incubating
public interface ProblemAggregationDescriptor extends BaseProblemDescriptor {

    /**
     * Returns the list of problem aggregations.
     * All Problems that occurred more than once during the build are aggregated and sent as a {@link ProblemAggregation}.
     *
     * @return The list of problem aggregations.
     * @since 8.6
     */
    List<ProblemAggregation> getAggregations();
}
