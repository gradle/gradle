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

/**
 * A builder for creating software type bindings as well as declaring build logic
 * associated with the binding.
 */
public interface SoftwareTypeBindingBuilder {
    /**
     * Create a binding for a software type definition object in the DSL with the provided name.
     * The supplied transform is used to implement the build logic associated with the binding.
     *
     * @param name the name of the binding.  This is how it will be referenced in the DSL.
     * @param dslType the class of the software type definition object
     * @param buildModelType the class of the build model object for this software type
     * @param transform the transform that maps the definition to the build model and implements the build logic associated with the feature
     * @return a {@link DslBindingBuilder} that can be used to further configure the binding
     * @param <T> the type of the software type definition object
     * @param <V> the type of the build model object for this software type
     */
    <T extends HasBuildModel<V>, V extends BuildModel> DslBindingBuilder<T, V> bindSoftwareType(
        String name,
        Class<T> dslType,
        Class<V> buildModelType,
        SoftwareTypeTransform<T, V> transform
    );

    /**
     * Create a binding for a software type definition object in the DSL with the provided name.
     * The supplied transform is used to implement the build logic associated with the binding.
     *
     * @param name the name of the binding.  This is how it will be referenced in the DSL.
     * @param dslType the class of the software type definition object
     * @param transform the transform that maps the definition to the build model and implements the build logic associated with the feature
     * @return a {@link DslBindingBuilder} that can be used to further configure the binding
     * @param <T> the type of the software type definition object
     * @param <V> the type of the build model object for this software type
     */
    <T extends HasBuildModel<V>, V extends BuildModel> DslBindingBuilder<T, V> bindSoftwareType(
        String name,
        Class<T> dslType,
        SoftwareTypeTransform<T, V> transform
    );
}
