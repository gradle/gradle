/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.tooling.model.internal.outcomes;

import javax.annotation.Nullable;

/**
 * Represents something that happens as part of a build.
 *
 * @since 1.2
 */
public interface GradleBuildOutcome {

    /**
     * An internal unique identifier for this outcome.
     *
     * The identifier must be unique for an outcome within the project that it is part of. Identifiers should
     * be deterministic, in that a build executed the same way twice should produce the same set of outcome IDs.
     *
     * The value of the id does not need to be meaningful to a user.
     *
     * @return The id of this outcome. Never null.
     */
    String getId();

    /**
     * A textual description of the outcome.
     *
     * @return The description. Never null.
     */
    String getDescription();

    /**
     * The path to the task that created the outcome, if it was known to be created by a task.
     *
     * @return The path to the task the “created” the outcome, or {@code null} if it was not produced by a task.
     */
    @Nullable
    String getTaskPath();

}
