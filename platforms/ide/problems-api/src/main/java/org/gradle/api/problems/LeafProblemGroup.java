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
 * A terminal problem group: problems can be reported into it, but it cannot have sub-groups.
 * <p>
 * Terminal groups are the third level of the hierarchy created through {@link SubProblemGroup#group(String)}, every synthetic
 * {@code Undefined} group, and the sub-groups of the closed {@link GradleProblemGroup}.
 * <p>
 * Not intended for subclassing outside of Gradle. New members may be added in future versions.
 *
 * @see ProblemGroups
 * @since 9.9.0
 */
@Incubating
public abstract class LeafProblemGroup extends ProblemGroup {

    /**
     * Constructor.
     *
     * @since 9.9.0
     */
    protected LeafProblemGroup() {
    }

    /**
     * The parent group. Never {@code null} for a terminal group.
     *
     * @since 9.9.0
     */
    @Override
    public abstract ProblemGroup getParent();

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
}
