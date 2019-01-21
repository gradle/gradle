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
import org.gradle.api.ActionConfiguration;
import org.gradle.api.Incubating;
import org.gradle.api.attributes.AttributeContainer;

/**
 * Defines an artifact transformation.
 *
 * @param <T> The transform specific configuration type.
 * @since 5.2
 */
@Incubating
public interface ArtifactTransformSpec<T> extends ActionConfiguration {
    /**
     * Attributes that match the variant that is consumed.
     */
    AttributeContainer getFrom();

    /**
     * Attributes that match the variant that is produced.
     */
    AttributeContainer getTo();

    /**
     * The parameters for the transform action.
     *
     * @since 5.3
     */
    T getParameters();

    /**
     * Configure the parameters for the transform action.
     *
     * @since 5.3
     */
    void parameters(Action<? super T> action);

    /**
     * Returns the {@link ArtifactTransform} implementation to use for this transform. Defaults to the value specified by the {@link TransformAction} annotation attached to the configuration object.
     */
    Class<? extends ArtifactTransform> getActionClass();

    void setActionClass(Class<? extends ArtifactTransform> implementationClass);
}
