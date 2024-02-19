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

package org.gradle.api.problems;

import org.gradle.api.Incubating;
import org.gradle.api.problems.internal.DefaultProblemGroup;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Represents a group of problems.
 * <p>
 * Groups are organized in hierarchy where the parent group should represent the more broad category.
 * <p>
 * Two group instances are considered equal if their Id's are the same and their parents are also equal to each other.
 * @since 8.8
 * @see ProblemId
 * @see DefaultProblemGroup
 */
@Incubating
@Immutable
public interface ProblemGroup {

    /**
     * The ID of the problem group.
     *
     * @since 8.8
     */
    String getId();

    /**
     * Returns a human-readable label describing the group.
     * <p>
     * The display name should not be considered when determining problem equality.
     * @since 8.8
     */
    String getDisplayName();

    /**
     * Returns the parent group or {@code null} for root groups.
     *
     * @since 8.8
     */
    @Nullable
    ProblemGroup getParent();
}
