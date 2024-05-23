/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.Incubating;

import javax.annotation.Nullable;
import java.util.List;

/**
 * interface descriping a problem.
 *
 * @since 8.9
 */
@Incubating
public interface ProblemDescription {
    /**
     * Returns the problem definition.
     *
     * @return the definition
     * @since 8.9
     */
    @Incubating
    ProblemDefinition getDefinition();

    /**
     * Returns the contextual label.
     *
     * @return the problem label
     * @since 8.9
     */
    @Incubating
    ContextualLabel getContextualLabel();

    /**
     * Returns the details string.
     *
     * @return the problem details
     * @since 8.9
     */
    @Incubating
    Details getDetails();

    /**
     * Returns the locations associated with this problem.
     *
     * @return the locations
     * @since 8.9
     */
    @Incubating
    List<Location> getLocations();

    /**
     * Returns the list of solutions.
     *
     * @return the solutions
     * @since 8.9
     */
    @Incubating
    List<Solution> getSolutions();

    /**
     * Returns the failure associated with this problem.
     *
     * @return the failure
     * @since 8.9
     */
    @Nullable
    @Incubating
    FailureContainer getFailure();
}
