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

package org.gradle.api.problems;

import org.gradle.api.Incubating;

/**
 * {@link Problem} instance builder requiring the specification of the problem group.
 *
 * @since 8.4
 */
@Incubating
public interface ProblemBuilderDefiningGroup {

    /**
     * Declares the problem group. A problem group defines the main category for the problems (e.g. deprecation, type validation, task selection ,etc.). The available group IDs are declared in
     * {@link ProblemGroup}.
     *
     * @param group the group ID
     * @return the builder for the next required property
     */
    ProblemBuilder group(String group);
}
