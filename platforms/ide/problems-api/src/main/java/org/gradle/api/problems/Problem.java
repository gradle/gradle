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

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Interface for describing structured information about a problem.
 *
 * @since 8.6
 */
@Incubating
public interface Problem {
    /**
     * Returns the problem category.
     *
     * @return the problem category.
     *
     * @since 8.6
     */
    ProblemCategory getProblemCategory();

    /**
     * The label of the problem.
     * <p>
     * Labels should be short and concise, so they fit approximately in a single line.
     *
     * @since 8.6
     */
    String getLabel();

    /**
     * A long description detailing the problem.
     * <p>
     * Details can elaborate on the problem, and provide more information about the problem.
     * They can be multiple lines long, but should not detail solutions; for that, use {@link #getSolutions()}.
     *
     * @since 8.6
     */
    @Nullable
    String getDetails();

    /**
     * Problem severity.
     * <p>
     * The severity of a problem is a hint to the user about how important the problem is.
     * ERROR will fail the build, WARNING will not.
     *
     * @since 8.6
     */
    Severity getSeverity();

    /**
     * Return the location data associated available for this problem.
     *
     * @since 8.6
     */
    List<ProblemLocation> getLocations();

    /**
     * A link to the documentation for this problem.
     *
     * @since 8.6
     */
    @Nullable
    DocLink getDocumentationLink();

    /**
     * A list of possible solutions the user can try to fix the problem.
     *
     * @since 8.6
     */
    List<String> getSolutions();

    /**
     * The exception that caused the problem.
     *
     * @since 8.6
     */
    @Nullable
    RuntimeException getException();

    /**
     * Additional data attached to the problem.
     * <p>
     * The only supported value type is {@link String}.
     *
     * @since 8.6
     */
    Map<String, Object> getAdditionalData();
}
