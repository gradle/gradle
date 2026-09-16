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
 * A predefined sub-group of the {@link GradleProblemGroup Gradle root group}, for example {@code Gradle > Build Cache}.
 * <p>
 * Problems can be reported into this group with {@link #problemId(String)}. Sub-groups cannot be created below it: it is not a
 * {@link SecondLevelProblemGroup}. Gradle may allow sub-groups below its own groups in a future version; that decision is taken for
 * this type alone and does not affect {@link ThirdLevelProblemGroup} or {@link UndefinedProblemGroup}.
 * <p>
 * Not intended for subclassing outside of Gradle. New members may be added in future versions.
 *
 * @see ProblemGroups
 * @since 9.9.0
 */
@Incubating
public abstract class GradleSecondLevelProblemGroup extends ProblemGroup {

    /**
     * Constructor.
     *
     * @since 9.9.0
     */
    protected GradleSecondLevelProblemGroup() {
    }

    /**
     * Creates a problem id in this group.
     * <p>
     * Problem names should be a single, concise sentence describing the kind of problem, without contextual details such as
     * file names or values; those belong in the contextual label, the details, and the locations of the reported problem.
     * They must not be blank, must not have leading or trailing whitespace, must not contain control or format characters
     * (other than the zero width joiner and non-joiner), and are limited to 2000 characters.
     *
     * @param name the name of the problem
     * @return the problem id
     * @throws IllegalArgumentException if the name is invalid
     * @since 9.9.0
     */
    public abstract ProblemId problemId(String name);
}
