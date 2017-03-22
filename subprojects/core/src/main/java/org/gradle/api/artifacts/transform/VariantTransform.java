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

package org.gradle.api.artifacts.transform;

import org.gradle.api.Action;
import org.gradle.api.ActionConfiguration;
import org.gradle.api.Incubating;
import org.gradle.api.attributes.AttributeContainer;

/**
 * Registration of an variant transform.
 *
 * @since 3.5
 */
@Incubating
public interface VariantTransform {
    /**
     * Attributes that match the variant that is consumed.
     */
    AttributeContainer getFrom();

    /**
     * Attributes that match the variant that is produced.
     */
    AttributeContainer getTo();

    /**
     * Action to transform artifacts for this variant transform.
     *
     * <p>An instance of the specified type is created for each file that is to be transformed. The class should provide a public zero-args constructor.</p>
     */
    void artifactTransform(Class<? extends ArtifactTransform> type);

    /**
     * Action to transform artifacts for this variant transform, potentially supplying some configuration to inject into the transform.
     *
     * <p>An instance of the specified type is created for each file that is to be transformed. The class should provide a public constructor that accepts the provided configuration.</p>
     */
    void artifactTransform(Class<? extends ArtifactTransform> type, Action<? super ActionConfiguration> configAction);
}
