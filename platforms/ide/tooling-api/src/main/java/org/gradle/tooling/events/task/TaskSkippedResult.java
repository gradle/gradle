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

import org.gradle.tooling.events.SkippedResult;

/**
 * Describes how a task operation was skipped.
 *
 * @since 2.5
 */
public interface TaskSkippedResult extends TaskOperationResult, SkippedResult {

    /**
     * Returns a message describing the reason for skipping the task.
     *
     * @return the message describing the skip reason
     */
    String getSkipMessage();

}
