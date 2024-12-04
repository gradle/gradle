/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.api.Action;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.artifacts.transform.TransformSpec;

import javax.annotation.Nullable;
import java.util.List;

public interface VariantTransformRegistry {
    /**
     * Register an artifact transform.
     *
     * @see TransformAction
     */
    default <T extends TransformParameters> void registerTransform(Class<? extends TransformAction<T>> actionType, Action<? super TransformSpec<T>> registrationAction) {
        registerTransform(null, actionType, registrationAction);
    }

    /**
     * Register an artifact transform with a name for error identification and reporting.
     *
     * @see TransformAction
     */
    <T extends TransformParameters> void registerTransform(@Nullable String name, Class<? extends TransformAction<T>> actionType, Action<? super TransformSpec<T>> registrationAction);

    List<TransformRegistration> getRegistrations();
}
