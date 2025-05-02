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

package org.gradle.internal.component.resolution.failure.interfaces;

import org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId;

/**
 * Represents any failure that can occur during variant selection and
 * contains contextual information specific to that type of failure.
 *
 * @implSpec This interface is meant only to be extended by other interfaces, it should not
 * be implemented directly.  Concrete types implementing this interface should be immutable data classes
 * without nullable fields.  They must also be serializable by the configuration cache.
 */
public interface ResolutionFailure {
    /**
     * Returns a human-readable description of the requested target component, for use
     * primarily in error messages.
     * <p>
     * If this is a {@link org.gradle.internal.component.resolution.failure.interfaces Component Selection} failure,
     * this should describe the requested component selector.  If this is
     * a {@link org.gradle.internal.component.resolution.failure.interfaces Variant Selection} failure,
     * this should describe the component identifier of the selected component.
     *
     * @return description of the requested target
     * {@link org.gradle.internal.component.resolution.failure.interfaces}
     */
    String describeRequestTarget();

    /**
     * Returns the problem id associated with this failure, for use in identifying the failure and
     * reporting it to the Problems API.
     *
     * @return the problem id
     */
    ResolutionFailureProblemId getProblemId();
}
