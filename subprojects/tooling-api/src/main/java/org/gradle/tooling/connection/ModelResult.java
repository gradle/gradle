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
import org.gradle.tooling.GradleConnectionException;

/**
 * The result of a model request.
 *
 * @param <T> Type of model in the result
 * @since 2.13
 */
@Incubating
public interface ModelResult<T> {
    /**
     * Returns the model produced.
     *
     * @return the model, never null.
     * @throws GradleConnectionException if the model could not be retrieved.
     */
    T getModel() throws GradleConnectionException;

    /**
     * The failure retrieving the model.
     * If this value is not null, further details can be obtained by casting to {@link FailedModelResult}.
     *
     * @return the failure, or null if the result was successful.
     */
    @Nullable
    GradleConnectionException getFailure();
}
