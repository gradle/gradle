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
 * Generates test events.
 *
 * @since 8.12
 */
@Incubating
public interface TestEventGenerator extends AutoCloseable {
    /**
     * Emit a start event for the test.
     *
     * @param startTime the time the test started
     * @since 8.12
     */
    void started(Instant startTime);

    /**
     * Emit a output event for the test. May be emitted multiple times.
     *
     * @param destination the destination of the output
     * @param output some output from the test
     * @since 8.12
     */
    void output(TestOutputEvent.Destination destination, String output);

    /**
     * Emit a failure event for the test. Does not imply the test has completed.
     *
     * @param failure the failure
     * @since 8.12
     */
    void failure(TestFailure failure);

    /**
     * Emit a completion event for the test.
     *
     * @param endTime the time the test completed
     * @param resultType the result of the test
     * @since 8.12
     */
    void completed(Instant endTime, TestResult.ResultType resultType);

    /**
     * Close the generator. No further events can be emitted after this.
     *
     * @since 8.12
     */
    @Override
    void close();
}
