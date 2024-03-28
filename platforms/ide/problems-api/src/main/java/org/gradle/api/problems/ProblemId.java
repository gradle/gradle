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

/**
 * Represents an identifier for a problem definition.
 * <p>
 * Two problem IDs are considered equal if their {@link #getName()} and their groups are equal.
 *
 * @since 8.8
 */
@Incubating
public interface ProblemId {

    /**
     * The name of the problem.
     *
     * @since 8.8
     */
    String getName();

    /**
     * A human-readable label describing the problem ID.
     *
     * @since 8.8
     */
    String getDisplayName();

    /**
     * The parent group.
     *
     * @since 8.8
     */
    ProblemGroup getGroup();
}
