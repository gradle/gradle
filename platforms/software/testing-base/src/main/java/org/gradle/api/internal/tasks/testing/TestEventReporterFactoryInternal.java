/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.file.Directory;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.tasks.testing.TestEventReporterFactory;
import org.gradle.internal.event.ListenerBroadcast;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.LongFunction;

@NullMarked
public interface TestEventReporterFactoryInternal extends TestEventReporterFactory {
    interface TestReportResult {
        final class FailureReported implements TestReportResult {
            private static final FailureReported INSTANCE = new FailureReported();

            private FailureReported() {
            }

            @Override
            public String toString() {
                return "FAILURE_REPORTED";
            }
        }

        final class NoAction implements TestReportResult {
            private static final NoAction INSTANCE = new NoAction();

            private NoAction() {
            }

            @Override
            public String toString() {
                return "NO_ACTION";
            }
        }

        final class TestFailureDetected implements TestReportResult {
            private final String failureMessage;

            private TestFailureDetected(String failureMessage) {
                if (failureMessage == null || failureMessage.isEmpty()) {
                    throw new IllegalArgumentException("Failure message must not be null or empty");
                }
                this.failureMessage = failureMessage;
            }

            @Override
            public Optional<String> getFailureMessage() {
                return Optional.of(failureMessage);
            }

            @Override
            public String toString() {
                return "TEST_FAILURE_DETECTED[failureMessage=" + failureMessage + "]";
            }

            @Override
            public boolean shouldFailTask() {
                return true;
            }
        }

        @NullMarked
        final class NoTestsRun implements TestReportResult {
            private final String failureMessage;

            private NoTestsRun(String failureMessage) {
                if (failureMessage == null || failureMessage.isEmpty()) {
                    throw new IllegalArgumentException("Failure message must not be null or empty");
                }
                this.failureMessage = failureMessage;
            }

            @Override
            public Optional<String> getFailureMessage() {
                return Optional.of(failureMessage);
            }

            @Override
            public String toString() {
                return "NO_TESTS_RUN";
            }

            @Override
            public boolean shouldFailTask() {
                return true;
            }
        }

        @NullMarked
        final class FailuresIgnored implements TestReportResult {
            private static final FailuresIgnored INSTANCE = new FailuresIgnored();

            private FailuresIgnored() {
            }

            @Override
            public String toString() {
                return "FAILURES_IGNORED";
            }
        }

        /**
         * A failure was reported, and no further reporting should be done.
         */
        static TestReportResult failureReported() {
            return FailureReported.INSTANCE;
        }

        /**
         * A test failure was detected, but it was not reported.
         * The default reporting for failure should be triggered with the provided failure message.
         *
         * @param failureMessage the message describing the test failure
         */
        static TestReportResult testFailureDetected(String failureMessage) {
            return new TestFailureDetected(failureMessage);
        }

        /**
         * No failure was detected or reported.
         * The default reporting for failure should be triggered if necessary.
         */
        static TestReportResult noAction() {
            return NoAction.INSTANCE;
        }

        /**
         * There <em>were</em> test failures, but the {@link AbstractTestTask#getIgnoreFailures()} flag was set.
         */
        static TestReportResult failuresIgnored() {
            return FailuresIgnored.INSTANCE;
        }

        /**
         * The test task did not run any tests and this was not expected or permitted (via filters and the {@code failOnNoDiscoveredTests} flag).
         *
         * @param failureMessage the message describing the reason no tests running is a failure
         */
        static TestReportResult noTestsRun(String failureMessage) {
            return new NoTestsRun(failureMessage);
        }

        /**
         * If this result represents a failure, returns the message to use for reporting the failure.
         *
         * @return the failure message, or an {@link Optional#empty()} if this result does not represent a failure
         */
        default Optional<String> getFailureMessage() {
            return Optional.empty();
        }

        /**
         * Whether the test task should be marked as failed when this is the result of the test reporting.
         *
         * @return {@code true} if the test task should be marked as failed; {@code false} otherwise
         */
        default boolean shouldFailTask() {
            return false;
        }
    }

    /**
     * Returns an object that can be used to report test events.
     * <p>
     * This method differs from {@link #createTestEventReporter(String, Directory, Directory, boolean)} in the following ways:
     * <ul>
     *     <li>The root descriptor can be provided.</li>
     *     <li>Only the result writer listener is used, added to {@code testListenerInternalBroadcaster}.</li>
     *     <li>The report generator can be configured.</li>
     *     <li>
     *       {@code diskSkipLevels} can be set, for merging the "Gradle Test Run" and "Gradle Executor" nodes, among others,
     *       in the on-disk results.
     *     </li>
     *     <li>
     *         {@code closeThrowsOnTestFailures} can be set to {@code false} to prevent throwing on close.
     *     </li>
     * </ul>
     *
     * @param rootDescriptor a function to create the root test descriptor
     * @param binaryResultsDirectory the directory to write binary test results to
     * @param reportGenerator the report generator to use
     * @param testListenerInternalBroadcaster the test listeners to notify of test events, may have additional listeners added
     * @param diskSkipLevels number of levels of the test tree to skip when writing to disk
     * @param closeThrowsOnTestFailures determines if this reporter should throw upon close if the root node has been failed, or do nothing
     * @return the test event reporter
     * @apiNote This API is used by {@link org.gradle.api.tasks.testing.AbstractTestTask} to support various extra features.
     * It may be worth considering these for public API in the future.
     * @since 9.3.0
     */
    GroupTestEventReporterInternal createInternalTestEventReporter(
        LongFunction<TestDescriptorInternal> rootDescriptor,
        Directory binaryResultsDirectory,
        @Nullable TestReportGenerator reportGenerator,
        ListenerBroadcast<TestListenerInternal> testListenerInternalBroadcaster,
        int diskSkipLevels,
        boolean closeThrowsOnTestFailures
    );
}
