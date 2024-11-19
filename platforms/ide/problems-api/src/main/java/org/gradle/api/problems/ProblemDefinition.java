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

import javax.annotation.Nullable;

/**
 * Describes a specific problem without context.
 * <p>
 * For example, in the domain of Java compilation problems, an unused variable warning could be described as such:
 * <ul>
 *     <li>category: compilation:java</li>
 *     <li>unused variable</li>
 *     <li>severity: WARNING</li>
 *     <li>...</li>
 * </ul>
 * <p>
 * The category and the label uniquely identify the problem definition, the remaining fields only supply additional information.
 *
 * @since 8.12
 */
@Incubating
public interface ProblemDefinition {

    /**
     * The problem ID.
     *
     * @since 8.12
     */
    ProblemId getId();

    /**
     * Problem severity.
     * <p>
     * The severity of a problem is a hint to the user about how important the problem is.
     * ERROR will fail the build, WARNING will not.
     *
     * @since 8.12
     */
    Severity getSeverity();

    /**
     * A link to the documentation for this problem.
     *
     * @since 8.12
     */
    @Nullable
    DocLink getDocumentationLink();
}
