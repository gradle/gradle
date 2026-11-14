/*
 * Copyright 2026 the original author or authors.
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
import org.jspecify.annotations.NullMarked;

import java.util.Collection;

/**
 * Information about the test a {@link TestReportEntryContributor} is contributing an entry for.
 *
 * <p>
 * The context is only valid during the call to the contributor.
 * </p>
 *
 * @since 9.10.0
 */
@Incubating
@NullMarked
public interface TestReportEntryContext {
    /**
     * The descriptor of the test being rendered.
     *
     * <p>
     * The parent of the descriptor is not available.
     * </p>
     *
     * @return the test descriptor
     * @since 9.10.0
     */
    TestDescriptor getTestDescriptor();

    /**
     * The result type of the test being rendered.
     *
     * @return the result type
     * @since 9.10.0
     */
    TestResult.ResultType getResultType();

    /**
     * The metadata events of the test being rendered.
     *
     * @return the metadata events
     * @since 9.10.0
     */
    Collection<TestMetadataEvent> getMetadataEvents();
}
