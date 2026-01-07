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

import org.gradle.api.tasks.testing.TestEventReporter;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestMetadataEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

@NullMarked
public interface TestEventReporterInternal extends TestEventReporter {
    /**
     * Emit a failure event for the test. May not be called before {@link #started(Instant)}.
     *
     * <p>
     * This allows passing the raw {@link org.gradle.api.tasks.testing.TestFailure TestFailures} to the reporter.
     * </p>
     *
     * @param endTime the time the test completed
     * @param failures the list of failures
     */
    void failed(Instant endTime, List<TestFailure> failures);

    /**
     * Emit a skipped event for the test when a test was skipped due to an assumption failure.
     * <p>
     * May not be called before {@link #started(Instant)}.
     *
     * @param endTime the time the test completed
     * @param assumptionFailure the assumption failure that caused the test to be skipped
     */
    void skipped(Instant endTime, @Nullable TestFailure assumptionFailure);

    /**
     * Internal method to send already formed metadata events
     */
    void metadata(TestMetadataEvent metadataEvent);
}
