/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.component.resolution.failure.interfaces;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributes;

/**
 * Represents a failure selecting an artifact variant for a selected graph variant
 * during the {@link org.gradle.internal.component.resolution.failure.interfaces Artifact Selection} part of dependency resolution.
 * <p>
 * When this failure occurs, we have always selected a component and a graph variant,
 * we are now attempting to select an artifact using a possibly different set of
 * attributes from those used during {@link org.gradle.internal.component.resolution.failure.interfaces Variant Selection}
 * to select the graph variant.
 */
public interface ArtifactSelectionFailure extends ResolutionFailure {
    /**
     * Gets the identifier of the component for which an artifact variant could not be selected.
     *
     * @return identifier for the component for which an artifact variant could not be selected
     */
    ComponentIdentifier getTargetComponent();

    /**
     * Gets the name of the variant for which an artifact could not be selected.
     *
     * @return name of the variant for which an artifact could not be selected
     */
    String getTargetVariant();

    /**
     * Gets the attributes that were used to attempt to select an artifact.
     * <p>
     * This is the combination of originally requested attributes during graph selection and any potential attribute modifications
     * performed by an {@link org.gradle.api.artifacts.ArtifactView ArtifactView} that is being used to select an artifact.
     *
     * @return the attributes that were used to attempt to select an artifact for this variant
     */
    ImmutableAttributes getRequestedAttributes();
}
