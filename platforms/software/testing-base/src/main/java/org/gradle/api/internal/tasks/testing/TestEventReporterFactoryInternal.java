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
import org.gradle.api.tasks.testing.GroupTestEventReporter;
import org.gradle.api.tasks.testing.TestEventReporterFactory;
import org.gradle.internal.event.ListenerBroadcast;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface TestEventReporterFactoryInternal extends TestEventReporterFactory {
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
     *     <li>Only the result writer listener is used, added to {@code testListenerInternalBroadcaster}.</li>
     *     <li>The report generator can be configured.</li>
     * </ul>
     *
     * @param rootName the name for the root node of the test tree
     * @param binaryResultsDirectory the directory to write binary test results to
     * @param reportGenerator the report generator to use
     * @param testListenerInternalBroadcaster the test listeners to notify of test events, may have additional listeners added
     *
     * @return the test event reporter
     *
     * @since 8.13
     *
     * @apiNote This API is used by {@link org.gradle.api.tasks.testing.AbstractTestTask} to support not generating HTML reports.
     */
    GroupTestEventReporter createInternalTestEventReporter(
        String rootName,
        Directory binaryResultsDirectory,
        TestReportGenerator reportGenerator,
        ListenerBroadcast<TestListenerInternal> testListenerInternalBroadcaster
    );
}
