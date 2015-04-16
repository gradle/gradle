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
 * Some information about having run a test with a failure.
 *
 * @since 2.4
 */
@Incubating
public interface TestFailure {

    /**
     * Returns the time when the test started execution.
     *
     * @return The start time, in milliseconds since the epoch
     */
    long getStartTime();

    /**
     * Returns the time when the test completed execution.
     *
     * @return The end time, in milliseconds since the epoch
     */
    long getEndTime();

    /**
     * The exceptions that occurred while running the test, if any.
     *
     * @return the exceptions, empty if the test failed without any exceptions
     */
    List<Failure> getFailures();

}
