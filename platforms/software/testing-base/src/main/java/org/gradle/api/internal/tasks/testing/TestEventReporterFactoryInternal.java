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
import org.jspecify.annotations.Nullable;

import java.util.function.LongFunction;

@NullMarked
public interface TestEventReporterFactoryInternal extends TestEventReporterFactory {
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
     *     <li>
     *         {@code addToAggregateReports} can be set to control if the results are automatically registered with the
     *         aggregate reporting facilities.
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
        boolean closeThrowsOnTestFailures,
        boolean addToAggregateReports
    );
}
