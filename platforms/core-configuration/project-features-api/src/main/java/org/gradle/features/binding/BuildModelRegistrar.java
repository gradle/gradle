/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.binding;

import org.gradle.api.Incubating;

/**
 * Provides explicit build model registration.
 * <p>
 * While a feature's primary definition object has its build model registered automatically,
 * nested definition objects (such as container elements) require explicit registration via this service.
 * <p>
 * This service is available for injection in unsafe apply actions.
 *
 * @since 9.6.0
 */
@Incubating
public interface BuildModelRegistrar {
    /**
     * Creates, registers, and returns a new build model instance for the given {@code definition} instance.
     * The build model implementation is created as a managed object of the definition's public build model type.
     * <p>
     * This method must only be used on nested definition objects, such as container elements, and not on a feature's primary definition object, which has its
     * build model registered automatically.
     * <p>
     * A build model must be registered for a definition before {@link ProjectFeatureApplicationContext#getBuildModel(Definition)} is used on it.
     *
     * @see #registerBuildModel(Definition, Class) the other overload to create a build model of a specific implementation type.
     *
     * @throws IllegalStateException if there is already a build model instance registered for the definition.
     *
     * @since 9.6.0
     */
    <T extends Definition<V>, V extends BuildModel> V registerBuildModel(T definition);

    /**
     * Creates, registers, and returns a new build model of the specific implementation type {@code implementationType} for the given {@code definition} instance.
     * <p>
     * This method must only be used on nested definition objects, such as container elements, and not on a feature's primary definition object, which has its
     * build model registered automatically.
     * <p>
     * A build model must be registered for a definition before {@link ProjectFeatureApplicationContext#getBuildModel(Definition)} is used on it.
     *
     * @throws IllegalStateException if there is already a build model instance registered for the definition.
     *
     * @since 9.6.0
     */
    <T extends Definition<V>, V extends BuildModel> V registerBuildModel(T definition, Class<? extends V> implementationType);
}
