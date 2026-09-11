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

package org.gradle.api.problems;

import org.gradle.api.Incubating;

/**
 * A problem group directly below a {@link RootProblemGroup}, either predefined (for example {@code Compilation > Java}) or
 * created through {@link RootProblemGroup#group(String)}.
 * <p>
 * Problems can be reported into this group with {@link #problem(String)}. A further, terminal level of sub-groups can be
 * created with {@link #group(String)}.
 * <p>
 * Not intended for subclassing outside of Gradle. New members may be added in future versions.
 *
 * @see ProblemGroups
 * @since 9.9.0
 */
@Incubating
public abstract class SubProblemGroup extends ProblemGroup {

    /**
     * Constructor.
     *
     * @since 9.9.0
     */
    protected SubProblemGroup() {
    }

    /**
     * The parent group. Never {@code null} for a sub-group.
     *
     * @since 9.9.0
     */
    @Override
    public abstract ProblemGroup getParent();

    /**
     * Returns the terminal sub-group with the given name. Terminal groups cannot have sub-groups of their own.
     * <p>
     * Names are case-sensitive. The name {@code Undefined} is reserved in any letter case; use {@link #getUndefined()} instead.
     * Group names must not be blank, must not have leading or trailing whitespace, must not contain control or format characters,
     * and are limited to 50 characters.
     *
     * @param name the name of the sub-group
     * @return the sub-group
     * @throws IllegalArgumentException if the name is invalid or reserved
     * @since 9.9.0
     */
    public abstract LeafProblemGroup group(String name);

    /**
     * Creates a problem id in this group.
     * <p>
     * Problem names should be a single, concise sentence. They must not be blank, must not have leading or trailing whitespace,
     * must not contain control or format characters, and are limited to 2000 characters.
     *
     * @param name the name of the problem
     * @return the problem id
     * @throws IllegalArgumentException if the name is invalid
     * @since 9.9.0
     */
    public abstract ProblemId problem(String name);

    /**
     * The synthetic {@code Undefined} sub-group for problems without an explicitly defined sub-group below this group.
     * <p>
     * Prefer reporting into this group directly with {@link #problem(String)}, or into a sub-group created with
     * {@link #group(String)}. This group exists so that a problem can be reported without inventing a category.
     *
     * @since 9.9.0
     */
    public abstract LeafProblemGroup getUndefined();
}
