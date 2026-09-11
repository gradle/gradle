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
 * A root problem group of the predefined hierarchy, for example {@code Compilation}.
 * <p>
 * Root groups only structure the hierarchy: problems cannot be reported directly into a root group. Report them into one of the
 * predefined sub-groups, into a sub-group created with {@link #group(String)}, or into {@link #getUndefined()}.
 * <p>
 * Not intended for subclassing outside of Gradle. New members may be added in future versions.
 *
 * @see ProblemGroups
 * @since 9.9.0
 */
@Incubating
public abstract class RootProblemGroup extends ProblemGroup {

    /**
     * Constructor.
     *
     * @since 9.9.0
     */
    protected RootProblemGroup() {
    }

    /**
     * Returns the sub-group with the given name, creating it if it is not one of the predefined sub-groups.
     * <p>
     * Names are case-sensitive. If {@code name} equals the name of a predefined sub-group, that predefined group is returned;
     * any other name, including one that only differs from a predefined sub-group by case, creates a new sub-group. The name
     * {@code Undefined} is reserved in any letter case; use {@link #getUndefined()} instead.
     * <p>
     * Group names must not be blank, must not have leading or trailing whitespace, must not contain control or format characters,
     * and are limited to 50 characters.
     *
     * @param name the name of the sub-group
     * @return the sub-group
     * @throws IllegalArgumentException if the name is invalid or reserved
     * @since 9.9.0
     */
    public abstract SubProblemGroup group(String name);

    /**
     * The synthetic {@code Undefined} sub-group for problems without an explicitly defined sub-group below this root group.
     * <p>
     * Prefer a predefined sub-group or a sub-group created with {@link #group(String)}. This group exists so that a problem
     * can be reported without inventing a category.
     *
     * @since 9.9.0
     */
    public abstract LeafProblemGroup getUndefined();
}
