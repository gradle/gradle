/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.artifacts;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.component.ComponentIdentifier;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Describes a resolution failure, giving access to context about the failure, when available.
 * @param <T> the component id type
 */
@Incubating
public interface ResolutionFailure<T extends ComponentIdentifier> {
    /**
     * Identifies the component that couldn't be resolved, or triggerred the resolution failure.
     * @return The component id on which the failure occurred, if applicable.
     */
    @Nullable
    T getComponentId();

    /**
     * Returns the list of locations that were tried, or null if it doesn't apply.
     * @return the list of attempted locations
     */
    @Nullable
    List<String> getAttemptedLocations();

    /**
     * The underlying exception, to be typically displayed to the advanced user.
     *
     * @return the problem, as an exception that might be inspected for further details.
     */
    Throwable getProblem();
}
