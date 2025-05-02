/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.artifacts.transform;

import org.gradle.api.Action;
import org.gradle.api.attributes.AttributeContainer;

/**
 * Base configuration for artifact transform registrations.
 *
 * @see org.gradle.api.artifacts.dsl.DependencyHandler#registerTransform(Class, Action)
 * @param <T> The transform specific parameter type.
 * @since 5.3
 */
public interface TransformSpec<T extends TransformParameters> {
    /**
     * Attributes that match the variant that is consumed.
     *
     * @see org.gradle.api.artifacts.dsl.DependencyHandler#registerTransform(Class, Action)
     */
    AttributeContainer getFrom();

    /**
     * Attributes that match the variant that is produced.
     *
     * @see org.gradle.api.artifacts.dsl.DependencyHandler#registerTransform(Class, Action)
     */
    AttributeContainer getTo();

    /**
     * The parameters for the transform action.
     *
     * @see org.gradle.api.artifacts.dsl.DependencyHandler#registerTransform(Class, Action)
     */
    T getParameters();

    /**
     * Configure the parameters for the transform action.
     * 
     * @see org.gradle.api.artifacts.dsl.DependencyHandler#registerTransform(Class, Action)
     */
    void parameters(Action<? super T> action);
}
