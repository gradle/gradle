/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.tooling.events.problems;

import org.gradle.api.Incubating;

/**
 * Represents a problem id.
 *
 * @since 8.9
 */
@Incubating
public interface ProblemId {

    /**
     * The name of the problem group.
     *
     * @return the label
     * @since 8.9
     */
    String getName();

    /**
     * Returns a human-readable label describing the group.
     *
     * @return the display name
     * @since 8.9
     */
    String getDisplayName();

    /**
     * Returns the container  group.
     *
     * @return the group
     * @since 8.9
     */
    ProblemGroup getGroup();
}
