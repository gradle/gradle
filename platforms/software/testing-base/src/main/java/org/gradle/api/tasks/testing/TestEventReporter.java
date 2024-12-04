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

package org.gradle.api.tasks.testing;

import org.gradle.api.Incubating;

import java.time.Instant;

/**
 * Reports test events.
 *
 * @since 8.12
 */
@Incubating
public interface TestEventReporter extends AutoCloseable {
    /**
     * Emit a start event for the test. Can only be called once, and must be followed by a call to {@link #succeeded(Instant)}, {@link #skipped(Instant)}, {@link #failed(Instant)},
     * or {@link #failed(Instant, String)}.
     *
     * @param startTime the time the test started
     * @since 8.12
     */
    void started(Instant startTime);

    /**
     * Emit an output event for the test. May be called multiple times. May not be called before {@link #started(Instant)}.
     *
     * @param logTime the time the output was logged, must be between the start and end times of the test
     * @param destination the destination of the output
     * @param output some output from the test
     * @since 8.12
     */
    void output(Instant logTime, TestOutputEvent.Destination destination, String output);

    /**
     * Emit an event containing metadata about the test or test group currently being run.
     * <p>
     * Producers can supply the same value as the test start time to indicate that the metadata is "timeless", such
     * as environment information that isn't tied to a specific point during test execution.  Otherwise, the time
     * should be between the start and end times of the test (inclusive), but this is not enforced.
     * <p>
     * Keys should usually be unique within the scope of a single test, but this is not enforced.
     *
     * @param logTime the time the metadata was logged, should be between the start and end times of the test (inclusive)
     * @param key a key to identify the metadata
     * @param value the metadata value, which must be serializable by the Tooling API
     * @since 8.12
     */
    void metadata(Instant logTime, String key, Object value);

    /**
     * Emit a successful completion event for the test. May not be called before {@link #started(Instant)}.
     *
     * @param endTime the time the test completed
     * @since 8.12
     */
    void succeeded(Instant endTime);

    /**
     * Emit a skipped event for the test. May not be called before {@link #started(Instant)}.
     *
     * @param endTime the time the test completed
     * @since 8.12
     */
    void skipped(Instant endTime);

    /**
     * Emit a failure event for the test. May not be called before {@link #started(Instant)}.
     *
     * @param endTime the time the test completed
     * @since 8.12
     */
    default void failed(Instant endTime) {
        failed(endTime, "");
    }

    /**
     * Emit a failure event for the test. May not be called before {@link #started(Instant)}.
     *
     * @param endTime the time the test completed
     * @param message the failure message
     * @since 8.12
     */
    default void failed(Instant endTime, String message) {
        failed(endTime, message, "");
    }

    /**
     * Emit a failure event for the test. May not be called before {@link #started(Instant)}.
     *
     * @param endTime the time the test completed
     * @param message the failure message
     * @param additionalContent additional content for the failure, like a stacktrace
     * @since 8.12
     */
    void failed(Instant endTime, String message, String additionalContent);

    /**
     * Close the generator. No further events can be emitted after this.
     *
     * @since 8.12
     */
    @Override
    void close();
}
