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

import org.gradle.api.tasks.testing.GroupTestEventReporter;
import org.gradle.api.tasks.testing.TestEventReporter;

/**
 * API for reporting tests that allows using existing descriptors rather than letting the reporter generate them.
 *
 * <p>
 * In the future, we should see if we can improve the {@link GroupTestEventReporter} API and remove this interface.
 * This may not be possible due to the way that {@code AbstractTestTask} functions, though.
 * </p>
 */
public interface GroupTestEventReporterInternal extends GroupTestEventReporter {
    /**
     * Create a child reporter for a test descriptor. This is used internally to ferry existing test descriptors
     * to the new event reporter API. Ideally, we should improve the API to avoid this.
     *
     * @param testDescriptor the test descriptor to report
     * @return a new event reporter for the test descriptor
     */
    TestEventReporter reportTestDirectly(TestDescriptorInternal testDescriptor);

    /**
     * Create a child reporter for a test descriptor. This is used internally to ferry existing test descriptors
     * to the new event reporter API. Ideally, we should improve the API to avoid this.
     *
     * @param testDescriptor the test descriptor to report
     * @return a new event reporter for the test descriptor
     */
    GroupTestEventReporter reportTestGroupDirectly(TestDescriptorInternal testDescriptor);
}
