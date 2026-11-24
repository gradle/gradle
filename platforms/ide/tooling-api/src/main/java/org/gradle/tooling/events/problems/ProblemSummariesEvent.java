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

import java.util.List;

/**
 * The event capturing all problems summaries.
 *
 * A summary will be sent for every problem id that exceeds the threshold amount.
 * This also means each task will only send events up to the threshold for each problem id.
 * Problems reported outside a task share one threshold.
 * The objects contain the problem id that exceeded the threshold and the amount by how much it was exceeded, added up over all tasks and problems reported outside a task.
 * <p>
 * In versions before Gradle 9.10.0, the threshold applies to the whole build.
 * <p>
 * Before the build finishes this event will be received even if the list is empty.
 *
 *
 *
 * @see ProblemSummary
 *
 * @since 8.12
 */
@Incubating
public interface ProblemSummariesEvent extends ProblemEvent {
    /**
     * Get problems summaries.
     *
     * There is one summary for each problem id that exceeded the threshold.
     *
     * @since 8.12
     */
    List<ProblemSummary> getProblemSummaries();
}
