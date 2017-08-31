/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.events.task;

import org.gradle.api.Incubating;
import org.gradle.tooling.events.SuccessResult;

/**
 * Describes how a task operation finished successfully.
 *
 * @since 2.5
 */
@Incubating
public interface TaskSuccessResult extends TaskOperationResult, SuccessResult {
    /**
     * Returns whether this task was up-to-date.
     *
     * @return {@code true} if this task was up-to-date
     */
    boolean isUpToDate();

    /**
     * Returns whether the output for this task was pulled from a build cache when using
     * <a href="https://docs.gradle.org/current/userguide/build_cache.html#sec:task_output_caching" target="_top">task output caching</a>.
     *
     * <p>NOTE: This will always be false if the Gradle version does
     * not support task output caching.</p>
     *
     * @return {@code true} if the output for this task was from a build cache
     * @since 3.3
     */
    boolean isFromCache();
}
