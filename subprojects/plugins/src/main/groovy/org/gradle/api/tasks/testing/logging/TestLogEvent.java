/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.testing.logging;

/**
 * Test events that can be logged.
 */
public enum TestLogEvent {
    /**
     * A test has started. This event gets fired both for atomic and composite tests.
     */
    STARTED,

    /**
     * A test has completed successfully. This event gets fired both for atomic and composite tests.
     */
    PASSED,

    /**
     * A test has been skipped. This event gets fired both for atomic and composite tests.
     */
    SKIPPED,

    /**
     * A test has failed. This event gets fired both for atomic and composite tests.
     */
    FAILED,

    /**
     * A test has written a message to standard out.
     */
    STANDARD_OUT,

    /**
     * A test has written a message to standard error.
     */
    STANDARD_ERROR
}
