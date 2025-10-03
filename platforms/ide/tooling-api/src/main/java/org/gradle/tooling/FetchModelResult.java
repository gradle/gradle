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

package org.gradle.tooling;

import org.gradle.api.Incubating;
import org.gradle.tooling.model.Model;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * Carries the result of a single model fetch operation.
 *
 * @param <T> The type of target from which the model was fetched.
 * @param <M> The fetched model type.
 * @since 9.3.0
 */
@Incubating
@NullMarked
public interface FetchModelResult<T extends Model, M> {

    /**
     * Returns the target from which the model was fetched.
     *
     * @return the target, or {@code null} if the fetch operation failed
     * @since 9.3.0
     */
    @Nullable
    @Incubating
    T getTarget();

    /**
     * Returns the fetched model.
     *
     * @return the model, or {@code null} if the fetch operation failed
     * @since 9.3.0
     */
    @Nullable
    @Incubating
    M getModel();

    /**
     * Returns the failures that occurred during the fetch operation.
     *
     * @return a collection of failures, empty if the fetch operation was successful
     * @since 9.3.0
     */
    @Incubating
    Collection<? extends Failure> getFailures();
}
