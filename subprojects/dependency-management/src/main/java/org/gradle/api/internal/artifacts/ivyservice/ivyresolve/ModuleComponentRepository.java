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

import org.gradle.api.artifacts.ComponentMetadataSupplier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;

import java.util.Map;

/**
 * A repository of module components.
 */
public interface ModuleComponentRepository {
    String getId();

    String getName();

    ModuleComponentRepositoryAccess getLocalAccess();

    ModuleComponentRepositoryAccess getRemoteAccess();

    ComponentMetadataSupplier createMetadataSupplier();

    // TODO - put this somewhere else
    Map<ComponentArtifactIdentifier, ResolvableArtifact> getArtifactCache();
}
