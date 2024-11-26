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

package org.gradle.api.problems.internal;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.problems.AdditionalData;
import org.gradle.internal.component.resolution.failure.interfaces.ResolutionFailure;

/**
 * {@link AdditionalData} data for a {@link Problem} that represents a resolution failure.
 * <p>
 * Serialized to JSON as a map with the following keys:
 * <ul>
 * <li>RequestTarget - a description of the target of the resolution request that failed</li>
 * <li>ProblemId - the id of the problem</li>
 * <li>ProblemDisplayName - a human-readable description of the problem</li>
 * </ul>
 */
public interface ResolutionFailureData extends GeneralData {
    /**
     * Getter for the resolution failure that caused the problem.
     *
     * @return the resolution failure
     */
    ResolutionFailure getResolutionFailure();

    @Override
    default ImmutableMap<String, String> getAsMap() {
        return ImmutableMap.<String, String>builder()
            .put("requestTarget", getResolutionFailure().describeRequestTarget())
            .put("problemId", getResolutionFailure().getProblemId().name())
            .put("problemDisplayName", getResolutionFailure().getProblemId().getDisplayName())
            .build();
    }
}
