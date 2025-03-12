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
import org.gradle.api.tasks.testing.TestEventReporterFactory;
import org.gradle.internal.event.ListenerBroadcast;
import org.jspecify.annotations.NullMarked;

import java.util.function.LongFunction;
import java.util.function.Supplier;

@NullMarked
public interface TestEventReporterFactoryInternal extends TestEventReporterFactory {
    interface FailureReportResult {
        final class FailureReported implements FailureReportResult {
            private static final FailureReported INSTANCE = new FailureReported();

            private FailureReported() {
            }

            @Override
            public String toString() {
                return "FAILURE_REPORTED";
            }
        }

        final class NoAction implements FailureReportResult {
            private static final NoAction INSTANCE = new NoAction();

            private NoAction() {
            }

            @Override
            public String toString() {
                return "NO_ACTION";
            }
        }

        final class TestFailureDetected implements FailureReportResult {
            private final String failureMessage;

            private TestFailureDetected(String failureMessage) {
                if (failureMessage == null || failureMessage.isEmpty()) {
                    throw new IllegalArgumentException("Failure message must not be null or empty");
                }
                this.failureMessage = failureMessage;
            }

            public String getFailureMessage() {
                return failureMessage;
            }

            @Override
            public String toString() {
                return "TEST_FAILURE_DETECTED[failureMessage=" + failureMessage + "]";
            }
        }

        /**
         * A failure was reported, and no further reporting should be done.
         */
        static FailureReportResult failureReported() {
            return FailureReported.INSTANCE;
        }

        /**
         * A test failure was detected, but it was not reported.
         * The default reporting for failure should be triggered with the provided failure message.
         *
         * @param failureMessage the message describing the test failure
         */
        static FailureReportResult testFailureDetected(String failureMessage) {
            return new TestFailureDetected(failureMessage);
        }

        /**
         * No failure was detected or reported.
         * The default reporting for failure should be triggered if necessary.
         */
        static FailureReportResult noAction() {
            return NoAction.INSTANCE;
        }
    }

    /**
     * Returns an object that can be used to report test events.
     *
     * <p>
     * When closed, it will throw if the root node has been failed.
     * </p>
     *
     * <p>
     * This method differs from {@link #createTestEventReporter(String, Directory, Directory)} in the following ways:
     * <ul>
     *     <li>The root descriptor can be provided.</li>
     *     <li>Only the result writer listener is used, added to {@code testListenerInternalBroadcaster}.</li>
     *     <li>The report generator can be configured.</li>
     *     <li>
     *       {@code skipFirstLevelOnDisk} can be set to {@code true}, for merging the "Gradle Test Run" and "Gradle Executor" nodes
     *       in the on-disk results.
     *     </li>
     *     <li>
     *         {@code detectOtherFailures} can be set to a handler that will be called to present some other failure to the user,
     *         rather than the default test failure. It will be called unconditionally when the reporter is closed.
     *     </li>
     * </ul>
     *
     * @param rootDescriptor a function to create the root test descriptor
     * @param binaryResultsDirectory the directory to write binary test results to
     * @param reportGenerator the report generator to use
     * @param testListenerInternalBroadcaster the test listeners to notify of test events, may have additional listeners added
     * @param skipFirstLevelOnDisk whether to flatten the top level of the test tree on disk
     * @param detectOtherFailures a handler to call to present some other failure to the user, rather than the default test failure
     * @return the test event reporter
     * @apiNote This API is used by {@link org.gradle.api.tasks.testing.AbstractTestTask} to support various extra features.
     * It may be worth considering these for public API in the future.
     * @since 8.13
     */
    GroupTestEventReporterInternal createInternalTestEventReporter(
        LongFunction<TestDescriptorInternal> rootDescriptor,
        Directory binaryResultsDirectory,
        TestReportGenerator reportGenerator,
        ListenerBroadcast<TestListenerInternal> testListenerInternalBroadcaster,
        boolean skipFirstLevelOnDisk,
        Supplier<FailureReportResult> detectOtherFailures
    );
}
