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
 * Represents am ID for a unique problem definition.
 * <p>
 * Problem IDs have a similar contracts as ProblemGroup. They have a container problem group returned by {@link #getParent()}.
 * Problem IDs are uniquely identified based on their ids and parents' ids.
 * <p>
 * The {@link #getParent()} method always returns a non-null value for problem IDs, but it cannot be expressed with Java annotations as it conflicts with the annotation on {@link ProblemGroup#getParent()}.
 *
 * @since 8.8
 */
@Incubating
public interface ProblemId extends ProblemGroup {

}
