/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.artifacts.ComponentMetadataSupplierDetails;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.internal.action.InstantiatingAction;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * A repository of module components.
 *
 * @param <T> the component resolution result type
 */
public interface ModuleComponentRepository<T> {
    /**
     * A unique identifier for this repository, based on its type and attributes.
     * Two repositories with the same configuration will share the same id.
     * This id is stable across builds on the same machine.
     *
     * <p>The name is not encoded in the id, as it is not relevant for resolution. The name is only used for diagnotics.</p>
     */
    String getId();

    /**
     * A user-friendly name for this repository.
     */
    String getName();

    /**
     * Accessor that attempts to locate module components without expensive network operations.
     */
    ModuleComponentRepositoryAccess<T> getLocalAccess();

    /**
     * Accessor that attempts to locate module components remotely, allowing expensive network operations.
     * This access will be disabled when Gradle is executed with `--offline`.
     */
    ModuleComponentRepositoryAccess<T> getRemoteAccess();

    // TODO - put this somewhere else
    Map<ComponentArtifactIdentifier, ResolvableArtifact> getArtifactCache();

    @Nullable
    InstantiatingAction<ComponentMetadataSupplierDetails> getComponentMetadataSupplier();
}
