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

package org.gradle.api.internal.plugins;

import java.util.Optional;

/**
 * Represents a declaration of a binding between a project feature DSL type, a build model type and an apply action.
 */
public interface ProjectFeatureBindingDeclaration<T extends Definition<V>, V extends BuildModel> {
    /**
     * The target definition type that the feature binds to.
     */
    TargetTypeInformation<?> targetDefinitionType();

    /**
     * The definition type that the feature is configured by.
     */
    Class<T> getDefinitionType();

    /**
     * The implementation type of the definition, if any.  This it the concrete class that will be instantiated
     * when the feature is configured in the DSL.
     */
    Optional<Class<? extends T>> getDefinitionImplementationType();

    /**
     * The build model type that the definition is bound to.
     */
    Class<V> getBuildModelType();

    /**
     * The implementation type of the build model, if any.  This is the concrete class that will be instantiated
     * to represent the build model for the feature.
     */
    Optional<Class<? extends V>> getBuildModelImplementationType();

    /**
     * The name of the feature binding.  This is the name used in the DSL to configure the feature.
     */
    String getName();

    /**
     * The action that applies the feature configuration to the build model and configures any build logic when the
     * feature is referenced.
     */
    ProjectFeatureApplyAction<T, ?, V> getTransform();

    /**
     * Whether the definition of this binding is considered safe or unsafe.
     */
    Safety getDefinitionSafety();

    /**
     * Represents the safety of some aspect of the binding.
     */
    enum Safety {
        SAFE,
        UNSAFE
    }
}
