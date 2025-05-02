/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.api.internal.GradleInternal;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public interface BuildTreeModelController {
    /**
     * Returns the mutable model, configuring if necessary.
     */
    GradleInternal getConfiguredModel();

    /**
     * Creates the model with a given parameter in the target scope.
     * <p>
     * The model builder is resolved in the target scope, configuring the scope if necessary.
     *
     * @return the created model (null is a valid model)
     * @throws UnknownModelException when the model builder cannot be found
     */
    @Nullable
    Object getModel(BuildTreeModelTarget target, String modelName, @Nullable Object parameter) throws UnknownModelException;

    boolean queryModelActionsRunInParallel();

    /**
     * Runs the given actions, possibly in parallel.
     *
     * @see #queryModelActionsRunInParallel()
     */
    <T> List<T> runQueryModelActions(List<Supplier<T>> actions);
}
