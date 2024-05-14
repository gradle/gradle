/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.api.internal.tasks.testing.DefaultTestFailure;

import java.util.List;

/**
 * Describes a test failure. Contains a reference to the failure and some structural information retrieved by the test worker.
 *
 * @since 7.6
 */
@Incubating
public abstract class TestFailure {

    /**
     * Returns the list of causes.
     * <p>
     * The result is typically non-empty for multi-assertion failures, e.g. for {@code org.test4j.MultipleFailuresError}, where the individual failures are in the returned list.
     *
     * @return the cause failures.
     */
    public abstract List<TestFailure> getCauses();

    /**
     * Returns the raw failure.
     *
     * @return the raw failure
     */
    public abstract Throwable getRawFailure();

    /**
     * Returns structural information about the failure.
     *
     * @return the failure structure
     */
    public abstract TestFailureDetails getDetails();

    /**
     * Creates a new TestFailure instance from an assertion failure.
     *
     * @param failure the assertion failure
     * @param expected the expected value for the failure; can be {@code null}
     * @param actual the actual value for the failure; can be {@code null}
     * @return the new instance
     */
    public static TestFailure fromTestAssertionFailure(Throwable failure, String expected, String actual) {
        return fromTestAssertionFailure(failure, expected, actual, null);
    }

    /**
     * Creates a new TestFailure instance from an assertion failure.
     *
     * @param failure the assertion failure
     * @param expected the expected value for the failure; can be {@code null}
     * @param actual the actual value for the failure; can be {@code null}
     * @param causes the list of cause failures; can be {@code null}
     * @return the new instance
     */
    public static TestFailure fromTestAssertionFailure(Throwable failure, String expected, String actual, List<TestFailure> causes) {
        return DefaultTestFailure.fromTestAssertionFailure(failure, expected, actual, causes);
    }

    /**
     * Todo
     * @since 8.3
     */
    public static TestFailure fromFileComparisonFailure(Throwable failure, String expected, String actual, byte[] expectedContent, byte[] actualContent, List<TestFailure> causes) {
        return DefaultTestFailure.fromFileComparisonTestAssertionFailure(failure, expected, actual, causes, expectedContent, actualContent);
    }

    /**
     * Creates a new TestFailure instance from a test framework failure.
     *
     * @param failure the failure
     * @return the new instance
     */
    public static TestFailure fromTestFrameworkFailure(Throwable failure) {
        return fromTestFrameworkFailure(failure, null);
    }

    /**
     * Creates a new TestFailure instance from a test framework failure.
     *
     * @param failure the failure
     * @param causes the list of cause failures; can be {@code null}
     * @return the new instance
     */
    public static TestFailure fromTestFrameworkFailure(Throwable failure, List<TestFailure> causes) {
        return DefaultTestFailure.fromTestFrameworkFailure(failure, causes);
    }
}
