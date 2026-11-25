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
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.jspecify.annotations.NullMarked;

/**
 * The lazy properties behind the eager getters and setters of the {@link TestFilter} of a test task.
 *
 * @see AbstractTestTaskInputs#getFilter()
 * @since 10.0.0
 */
@Incubating
@NullMarked
public interface TestFilterInputs {

    /**
     * The included test name patterns.
     *
     * @see TestFilter#getIncludePatterns()
     * @since 10.0.0
     */
    SetProperty<String> getIncludePatterns();

    /**
     * The excluded test name patterns.
     *
     * @see TestFilter#getExcludePatterns()
     * @since 10.0.0
     */
    SetProperty<String> getExcludePatterns();

    /**
     * Whether the task fails if no test matches the filter.
     *
     * @see TestFilter#isFailOnNoMatchingTests()
     * @since 10.0.0
     */
    Property<Boolean> getFailOnNoMatchingTests();
}
