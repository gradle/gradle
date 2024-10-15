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

import javax.annotation.Nullable;

/**
 * Represents a problem definition.
 *
 * @since 8.9
 */
@Incubating
public interface ProblemDefinition {

    /**
     * The id of the problem.
     * @return the id.
     *
     * @since 8.9
     */
    ProblemId getId();

    /**
     * Problem severity.
     * <p>
     * The severity of a problem is a hint to the user about how important the problem is.
     * ERROR will fail the build, WARNING will not.
     *
     *  @since 8.9
     */
    Severity getSeverity();

    /**
     * A link to the documentation for this problem.
     *
     * @since 8.9
     */
    @Nullable
    DocumentationLink getDocumentationLink();
}
