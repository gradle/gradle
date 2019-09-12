/*
 * Copyright 2010 the original author or authors.
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

import javax.annotation.Nullable;
import java.util.List;

/**
 * Describes a test result.
 */
public interface TestResult {
    /**
     * The final status of a test.
     */
    public enum ResultType {
        SUCCESS, FAILURE, SKIPPED
    }

    /**
     * Returns the type of result.  Generally one wants it to be SUCCESS!
     *
     * @return The result type.
     */
    ResultType getResultType();

    /**
     * If the test failed with an exception, this will be the exception.  Some test frameworks do not fail without an
     * exception (JUnit), so in those cases this method will never return null.
     *
     * @return The exception, if any, logged for this test.  If none, a null is returned.
     */
    @Nullable
    Throwable getException();

    /**
     * If the test failed with any exceptions, this will contain the exceptions.  Some test frameworks do not fail
     * without an exception (JUnit), so in those cases this method will never return an empty list.
     *
     * @return The exceptions, if any, logged for this test.  If none, an empty list is returned.
     */
    List<Throwable> getExceptions();

    /**
     * Returns the time when this test started execution.
     *
     * @return The start time, in milliseconds since the epoch.
     */
    long getStartTime();

    /**
     * Returns the time when this test completed execution.
     *
     * @return The end t ime, in milliseconds since the epoch.
     */
    long getEndTime();

    /**
     * Returns the total number of atomic tests executed for this test. This will return 1 if this test is itself an
     * atomic test.
     *
     * @return The number of tests, possibly 0
     */
    long getTestCount();

    /**
     * Returns the number of successful atomic tests executed for this test.
     *
     * @return The number of tests, possibly 0
     */
    long getSuccessfulTestCount();

    /**
     * Returns the number of failed atomic tests executed for this test.
     *
     * @return The number of tests, possibly 0
     */
    long getFailedTestCount();

    /**
     * Returns the number of skipped atomic tests executed for this test.
     *
     * @return The number of tests, possibly 0
     */
    long getSkippedTestCount();

    String getOutput();
}
