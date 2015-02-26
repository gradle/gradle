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

package org.gradle.tooling;

import org.gradle.api.Incubating;

import java.util.List;

/**
 * Describes the result of running a test.
 *
 * @since 2.4
 */
@Incubating
public interface TestResult {

    /**
     * The id of the result type. The id can be resolved through {@link TestResult.ResultType#valueOf(int)}.
     *
     * @return the id of the result type
     * @see ResultType
     */
    int getResultTypeId();

    /**
     * Returns the time when this test started execution.
     *
     * @return The start time, in milliseconds since the epoch.
     */
    long getStartTime();

    /**
     * Returns the time when this test completed execution.
     *
     * @return The end time, in milliseconds since the epoch.
     */
    long getEndTime();

    /**
     * If the test failed with an exception, this will be the exception.
     *
     * @return the exception, null if the test succeeded or the test failed without an exception
     */
    Throwable getException();

    /**
     * If the test failed with any exceptions, this will contain the exceptions.
     *
     * @return the exception, null if the test succeeded or the test failed without an exception
     */
    List<Throwable> getExceptions();

    /**
     * Enumerates the different types of test results.
     */
    enum ResultType {

        SUCCESS(10), FAILURE(20), SKIPPED(30);

        private int id;

        ResultType(int id) {
            this.id = id;
        }

        public static ResultType valueOf(int id) {
            for (ResultType resultType : values()) {
                if (resultType.id == id) {
                    return resultType;
                }
            }
            throw new IllegalArgumentException(String.format("No ResultType with id %d.", id));
        }

    }


}
