/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.component.local.model;

import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.internal.component.model.ComponentGraphResolveState;

import javax.annotation.concurrent.ThreadSafe;

/**
 * A specialized {@link ComponentGraphResolveState} for local components (ie project dependencies).
 *
 * <p>Instances of this type are cached and reused for multiple graph resolutions, possibly in parallel. This means that the implementation must be thread-safe.
 */
@ThreadSafe
public interface LocalComponentGraphResolveState extends ComponentGraphResolveState {

    /**
     * Get the variant with the given name that may be used as the root of a dependency graph.
     *
     * TODO: This functionality should be separate from the component. We should be able to create
     * root variants without the knowledge of the component. This is blocked by the behavior where
     * a root variant can be selected by dependencies. This behavior is deprecated and will be an
     * error in Gradle 9.0.
     *
     * @throws IllegalArgumentException If no such variant exists.
     */
    LocalVariantGraphResolveState getRootVariant(String name);

    ModuleVersionIdentifier getModuleVersionId();

    @Override
    LocalComponentGraphResolveMetadata getMetadata();

    /**
     * Copies this state, but with the new component ID and the artifacts transformed by the given transformer.
     */
    LocalComponentGraphResolveState copy(ComponentIdentifier componentIdentifier, Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifacts);

    /**
     * @see LocalComponentGraphResolveState#reevaluate()
     */
    void reevaluate();
}
