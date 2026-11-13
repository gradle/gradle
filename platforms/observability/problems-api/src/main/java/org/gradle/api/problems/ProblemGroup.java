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
import org.gradle.api.problems.internal.LegacyProblemIdentityUsage;
import org.gradle.api.problems.internal.ProblemGroupInternal;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.Immutable;

/**
 * Represents a group of problems.
 * <p>
 * Groups are organized in hierarchy where the parent group should represent the more broad problem group.
 * <p>
 * Two problem groups are considered equal if their {@link #getName()} and their parents are equal.
 * <p>
 * Gradle provides a predefined hierarchy of groups, see {@link ProblemGroups}.
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
     * @deprecated Subclassing ProblemGroup outside of Gradle is deprecated and will not be supported in Gradle 10.
     * Use the groups from {@link Problems#getGroups()} instead.
     */
    @Deprecated
    protected ProblemGroup() {
        // clients should use the groups from Problems.getGroups()
        // Gradle 10: make this constructor package-private instead of removing it, since removing it would make javac
        // generate a public one. Subclasses in this package keep working; internal subclasses must move into this package.
        if (!(this instanceof ProblemGroupInternal)) {
            LegacyProblemIdentityUsage.used(LegacyProblemIdentityUsage.Usage.GROUP_SUBCLASS);
        }
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
     * @deprecated Use {@link #getName()} instead. The display name of a group from {@link Problems#getGroups()} is its name.
     * This method will be removed in Gradle 10.
     */
    @Deprecated
    public abstract String getDisplayName();

    /**
     * Returns the parent group or {@code null} for root groups.
     *
     * @since 8.8
     * @deprecated The groups from {@link Problems#getGroups()} have a fixed place in the predefined hierarchy, so there is no need to walk it.
     * This method will be removed in Gradle 10 without replacement.
     */
    @Deprecated
    @Nullable
    public abstract ProblemGroup getParent();

    /**
     * Creates a new root problem group i.e. a group with no parent.
     *
     * @param name the name of the group. Cannot be blank (i.e., {@code null}, empty string, or only whitespaces).
     * @param displayName the user-friendly display name of the group. Cannot be blank (i.e., {@code null}, empty string, or only whitespaces).
     * @return the new group
     * @since 8.13
     * @deprecated Use the predefined groups from {@link Problems#getGroups()} instead. For problems that fit no predefined root group,
     * create a sub-group with {@code getGroups().getOthers().group(name)}, see {@link RootProblemGroup#group(String)}.
     * This method will be removed in Gradle 11.
     */
    @Deprecated
    public static ProblemGroup create(String name, String displayName) {
        return new DefaultProblemGroup(name, displayName, null);
    }

    /**
     * Creates a new problem group.
     *
     * @param name the name of the group. Cannot be blank (i.e., {@code null}, empty string, or only whitespaces).
     * @param displayName the user-friendly display name of the group. Cannot be blank (i.e., {@code null}, empty string, or only whitespaces).
     * @param parent the parent group. May be {@code null} for root groups.
     * @return the new group
     * @since 8.13
     * @deprecated Use the predefined groups from {@link Problems#getGroups()} instead, and create sub-groups with
     * {@link RootProblemGroup#group(String)} or {@link SecondLevelProblemGroup#group(String)}.
     * This method will be removed in Gradle 11.
     */
    @Deprecated
    public static ProblemGroup create(String name, String displayName, @Nullable ProblemGroup parent) {
        return new DefaultProblemGroup(name, displayName, parent);
    }
}
