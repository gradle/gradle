/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.tasks;

import org.gradle.api.GradleException;
import org.gradle.api.Incubating;

/**
 * Signals that a task has failed in a manner which does not prevent consumers of that task's output from running.
 *
 * A task can depend upon another task's outcome (PASS vs. FAIL), outputs (the files they produce) or both. A verification
 * failure represents the case where a task has a failed outcome, but has still produced valid output files for consumption.
 * Tasks that only depend on the other task's outputs are allowed to execute. Tasks that depend on both the outcome and
 * output cannot execute.
 *
 * These failures should be caused by user code under analysis (such as from running tests, code quality checks, or linting errors).
 * A failed test, for instance, will cause a failing outcome for the test task, but this does not prevent another task
 * from reading and processing the (valid) test results output it produced (perhaps to aggregate multiple test reports).
 *
 * Verification failures do not represent a bug in either the build tool or custom task logic; the responsibility falls to the
 * project's software engineer to correct the verification failure.
 *
 * @since 7.4
 */
public class VerificationException extends GradleException {
    public VerificationException(String message) {
        super(message);
    }

    /**
     * Allows a cause to be specified.
     *
     * @since 8.2
     */
    @Incubating
    public VerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
