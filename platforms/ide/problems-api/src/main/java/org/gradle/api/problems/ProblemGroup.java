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
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.Immutable;

/**
 * Represents a group of problems.
 * <p>
 * Groups are organized in hierarchy where the parent group should represent the more broad problem group.
 * <p>
 * Two problem groups  are considered equal if their {@link #getName()} and their parents are equal.
 *
 * @since 8.8
 * @see ProblemId
 */
@Incubating
@Immutable
public abstract class ProblemGroup {

    /**
     * Constructor.
     *
     * @since 8.13
     */
    protected ProblemGroup() {
        // clients should use the static create() method
    }

    /**
     * The name of the problem group.
     *
     * @since 8.8
     */
    public abstract String getName();

    /**
     * Returns a human-readable label describing the group.
     *
     * @since 8.8
     */
    public abstract String getDisplayName();

    /**
     * Returns the parent group or {@code null} for root groups.
     *
     * @since 8.8
     */
    @Nullable
    public abstract ProblemGroup getParent();

    /**
     * Creates a new root problem i.e. a group with no parent.
     *
     * @param name the name of the group. The convention is to use kebab-case (ie lower case with hyphens).
     * @param displayName the user-friendly display name of the group
     * @return the new group
     * @since 8.13
     */
    public static ProblemGroup create(String name, String displayName) {
        return new DefaultProblemGroup(name, displayName, null);
    }

    /**
     * Creates a new problem group.
     *
     * @param name the name of the group. The convention is to use kebab-case (ie lower case with hyphens).
     * @param displayName the user-friendly display name of the group
     * @param parent the parent group
     * @return the new group
     * @since 8.13
     */
    public static ProblemGroup create(String name, String displayName, @Nullable ProblemGroup parent) {
        return new DefaultProblemGroup(name, displayName, parent);
    }
}
