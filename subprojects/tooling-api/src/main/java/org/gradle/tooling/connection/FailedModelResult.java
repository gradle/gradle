/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.connection;

import org.gradle.api.Incubating;
import org.gradle.api.Nullable;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.ProjectIdentifier;

/**
 * The result of a failed model request.
 *
 * @param <T> Type of model in the result
 * @since 2.13
 */
@Incubating
public interface FailedModelResult<T> extends ModelResult<T> {
    /**
     * Identifier of the build that originated this failure.
     *
     * @return the build identifier, never null.
     */
    BuildIdentifier getBuildIdentifier();

    /**
     * Identifier of the project that originated this failure, if any.
     *
     * @return the project identifier, or null if this failure did not originate from a particular project.
     */
    @Nullable
    ProjectIdentifier getProjectIdentifier();
}
