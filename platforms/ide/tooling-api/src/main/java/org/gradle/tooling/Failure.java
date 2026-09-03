/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.tooling;

import org.gradle.api.Incubating;
import org.gradle.tooling.events.problems.Problem;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Represents a failure. Failures are similar to exceptions but carry less information (only a message, a description and a cause) so
 * they can be used in a wider scope than just the JVM where the exception failed.
 *
 * @since 2.4
 */
public interface Failure {

    /**
     * Returns a short message (typically one line) for the failure.
     *
     * @return the failure message
     * @since 2.4
     */
    @Nullable
    String getMessage();

    /**
     * Returns a long description of the failure. For example, a stack trace.
     *
     * @return a long description of the failure
     * @since 2.4
     */
    @Nullable
    String getDescription();

    /**
     * Returns a long description of this failure node alone. For example, the failure header, its stack frames, and
     * any suppressed exceptions, but not the descriptions of the failures returned by {@link #getCauses()}.
     * <p>
     * Unlike {@link #getDescription()}, which may contain the text of the whole cause subtree, this method can be used
     * to inspect the description of every node in a failure tree without processing cause descriptions repeatedly.
     * <p>
     * This information is not available from Gradle providers earlier than 9.7, in which case this method returns
     * {@code null}.
     *
     * @return a long description of this failure node, or {@code null} if it is not available
     * @since 9.8.0
     */
    @Nullable
    @Incubating
    String getOwnDescription();

    /**
     * Returns the underlying causes for this failure, if any.
     *
     * @return the causes for this failure. Returns an empty list if this failure has no causes.
     * @since 2.4
     */
    List<? extends Failure> getCauses();

    /**
     * Returns the problems associated with this failure.
     * @return The problems, or an empty list if there are no problems.
     *
     * @since 8.12
     */
    @Incubating
    List<Problem> getProblems();
}
