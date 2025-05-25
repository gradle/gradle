/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.operations.problems;

import org.jspecify.annotations.Nullable;

/**
 * Describes a specific problem without a concrete usage.
 * <p>
 * For example, in the domain of Java compilation problems, an unused variable warning could be described as such:
 * <ul>
 *     <li>group: compilation:java</li>
 *     <li>unused variable</li>
 *     <li>severity: WARNING</li>
 *     <li>...</li>
 * </ul>
 * <p>
 * The group and the name uniquely identify the problem definition, the remaining fields only supply additional information.
 *
 * @since 8.14
 */
public interface ProblemDefinition {

    /**
     * The name of the problem.
     * <p>
     * The name should be used to categorize a set of problems.
     * The name itself does not need to be unique, the uniqueness is determined the name and the group hierarchy.
     *
     * @since 8.14
     */
    String getName();

    /**
     * A human-readable label describing the problem ID.
     * <p>
     * The display name should be used to present the problem to the user.
     *
     * @since 8.14
     */
    String getDisplayName();

    /**
     * The group of the problem.
     *
     * @since 8.14
     */
    ProblemGroup getGroup();

    /**
     * A link to the documentation for this problem.
     *
     * @since 8.14
     */
    @Nullable
    DocumentationLink getDocumentationLink();

}
