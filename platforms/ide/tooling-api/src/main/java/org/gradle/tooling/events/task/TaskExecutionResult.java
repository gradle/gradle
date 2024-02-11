/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.tooling.model.UnsupportedMethodException;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Describes the result of a non-skipped task.
 *
 * @since 5.1
 */
public interface TaskExecutionResult extends TaskOperationResult {

    /**
     * Returns whether this task was executed incrementally.
     *
     * @return {@code true} if this task was executed incrementally
     * @throws UnsupportedMethodException For Gradle versions older than 5.1, where this method is not supported.
     */
    boolean isIncremental();

    /**
     * Returns the reasons why this task was executed.
     *
     * @return the reasons why this task was executed; an empty list indicates the task was up-to-date;
     *         {@code null} that it failed before up-to-date checks had been performed.
     * @throws UnsupportedMethodException For Gradle versions older than 5.1, where this method is not supported.
     */
    @Nullable
    List<String> getExecutionReasons();

}
