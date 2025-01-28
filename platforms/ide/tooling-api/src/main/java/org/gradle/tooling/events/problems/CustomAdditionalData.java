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

package org.gradle.tooling.events.problems;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

/**
 * Custom Additional data for a problem.
 * <p>
 * This class allows to access additional data via a custom type that was attached to a problem by using {@link org.gradle.api.problems.ProblemSpec#additionalData(Class, Action)}.
 *
 * @since 8.13
 */
@Incubating
public interface CustomAdditionalData extends AdditionalData {
    /**
     * Returns an instance of the given type that accesses the additional data.
     *
     * @since 8.13
     */
    <T> T get(Class<T> type);
}
