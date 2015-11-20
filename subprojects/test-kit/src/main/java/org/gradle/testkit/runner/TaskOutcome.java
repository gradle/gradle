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

package org.gradle.testkit.runner;

import org.gradle.api.Incubating;

/**
 * The outcome of executing a task during a build.
 *
 * @since 2.6
 * @see BuildTask#getOutcome()
 */
@Incubating
public enum TaskOutcome {

    /**
     * That task executed and performed its actions without failure.
     */
    SUCCESS,

    /**
     * The task attempted to execute, but did not complete successfully.
     */
    FAILED,

    /**
     * The task was not executed, as its output was up to date.
     */
    UP_TO_DATE,

    /**
     * The task was not executed due to some reason.
     *
     * A task may be skipped if it had no work to do (e.g. no source to compile).
     */
    SKIPPED
}
