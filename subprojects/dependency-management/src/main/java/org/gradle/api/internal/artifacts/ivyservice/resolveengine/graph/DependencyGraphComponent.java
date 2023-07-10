/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * A {@link ResolvedGraphComponent} that is used during the resolution of the dependency graph.
 * Additional fields in this interface are not required to reconstitute the serialized graph.
 */
public interface DependencyGraphComponent extends ResolvedGraphComponent {
    /**
     * Returns the meta-data for the component. Resolves if not already resolved.
     *
     * @return null if the meta-data is not available due to some failure.
     */
    @Nullable
    ComponentGraphResolveMetadata getMetadataOrNull();

    Collection<? extends DependencyGraphComponent> getDependents();

    /**
     * Returns all versions that were seen for this component during
     * resolution.
     */
    Collection<? extends ModuleVersionIdentifier> getAllVersions();
}
