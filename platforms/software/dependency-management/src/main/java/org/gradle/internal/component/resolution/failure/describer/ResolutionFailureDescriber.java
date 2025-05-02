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

package org.gradle.internal.component.resolution.failure.describer;

import org.gradle.internal.component.resolution.failure.exception.AbstractResolutionFailureException;
import org.gradle.internal.component.resolution.failure.interfaces.ResolutionFailure;

/**
 * Describe a certain type of resolution failure, by providing concise and specific human-readable
 * information to the message of the resulting exception, and also adding resolution suggestions if possible.
 *
 * @param <FAILURE> the type of failure that this describer can describe
 */
public interface ResolutionFailureDescriber<FAILURE extends ResolutionFailure> {
    /**
     * Tests whether this describer can be applied to a given failure.
     *
     * This defaults to {@code true} as many describers are applicable to all instances of a single failure type
     * (and only to instances of that failure type) and will only be called upon to describe failures of that type.
     *
     * @return {@code true} if this describer can describe the given failure; {@code false} otherwise
     */
    default boolean canDescribeFailure(FAILURE failure) {
        return true;
    }

    /**
     * Describe the given failure.
     *
     * This method should only be called if {@link #canDescribeFailure(ResolutionFailure)} returns {@code true}
     * for the given failure.
     *
     * @param failure the failure to describe
     *
     * @return the exception that describes the failure
     *
     * @implSpec Testing {@link #canDescribeFailure(ResolutionFailure)} should <strong>NOT</strong> be done by
     * implementations of this method; ensuring this is done first is the responsibility of the caller.
     */
    AbstractResolutionFailureException describeFailure(FAILURE failure);
}
