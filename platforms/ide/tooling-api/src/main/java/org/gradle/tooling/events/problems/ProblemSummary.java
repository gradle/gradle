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
 * A problem summary.
 *
 * @see ProblemSummariesEvent
 * @since 8.12
 */
@Incubating
public interface ProblemSummary {

    /**
     * The problem id that exceeded the threshold for the number of allowed events, in a task or outside of tasks.
     *
     * @since 8.12
     */
    ProblemId getProblemId();

    /**
     * The number of distinct problems with this id that exceeded the threshold and were not sent as events.
     * <p>
     * Since Gradle 9.10.0, the threshold applies to each task, and problems reported outside a task share one threshold.
     * This count adds up what all of them exceeded it by.
     * The total number of distinct problems is the number of events received for this id plus this count.
     * A problem counts once for each task that reports it.
     * <p>
     * In earlier versions, the threshold applies to the whole build, so the total is <code>threshold + count</code>.
     *
     * @since 8.12
     */
    Integer getCount();
}
