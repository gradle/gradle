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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.scan.UsedByScanPlugin;

/**
 * A processor for test results. Implementations are not required to be thread-safe.
 */
@UsedByScanPlugin("test-distribution")
public interface TestResultProcessor {
    /**
     * Notifies this processor that a test has started execution.
     */
    @UsedByScanPlugin("test-distribution")
    void started(TestDescriptorInternal test, TestStartEvent event);

    /**
     * Notifies this processor that a test has completed execution.
     */
    @UsedByScanPlugin("test-distribution")
    void completed(Object testId, TestCompleteEvent event);

    /**
     * Notifies this processor that a test has produced some output.
     */
    @UsedByScanPlugin("test-distribution")
    void output(Object testId, TestOutputEvent event);

    /**
     * Notifies this processor that a failure has occurred in the given test.
     */
    @UsedByScanPlugin("test-distribution")
    void failure(Object testId, Throwable result);
}
