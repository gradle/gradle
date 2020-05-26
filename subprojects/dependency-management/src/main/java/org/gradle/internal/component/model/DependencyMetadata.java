/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.component.model;

import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

/**
 * A dependency that can participate in dependency resolution.
 * Note that various subtypes provide additional details, but these are not required by the core resolution engine.
 */
public interface DependencyMetadata {
    /**
     * Returns the component selector for this dependency.
     *
     * @return Component selector
     */
    ComponentSelector getSelector();

    /**
     * Select the target configurations for this dependency from the given target component.
     */
    List<ConfigurationMetadata> selectConfigurations(ImmutableAttributes consumerAttributes, ComponentResolveMetadata targetComponent, AttributesSchemaInternal consumerSchema, Collection<? extends Capability> explicitRequestedCapabilities);

    /**
     * Returns a view of the excludes filtered for this dependency in this configuration.
     */
    List<ExcludeMetadata> getExcludes();

    /**
     * Returns the artifacts referenced by this dependency, if any.
     * When a dependency references artifacts, those artifacts are used in place of the default artifacts of the target component.
     * In most cases, it makes sense for this set to be empty, and for all of the artifacts of the target component to be included.
     */
    List<IvyArtifactName> getArtifacts();

    /**
     * Returns a copy of this dependency with the given target.
     */
    DependencyMetadata withTarget(ComponentSelector target);

    DependencyMetadata withTargetAndArtifacts(ComponentSelector target, List<IvyArtifactName> artifacts);

    /**
     * Is the target component of this dependency considered 'changing'.
     */
    boolean isChanging();

    /**
     * Should the dependency be resolved transitively?
     * A false value is effectively equivalent to a wildcard exclusion.
     */
    boolean isTransitive();

    /**
     * Is this a strong dependency, does it is merely a constraint on the module to select if brought in
     * by another dependency? ("Optional" dependencies are "constraints")
     */
    boolean isConstraint();

    /**
     * Is this a dependency that "pulls up" strict version constraints from the target node?
     */
    boolean isEndorsingStrictVersions();

    /**
     * An optional human readable reason why this dependency is used.
     * @return if not null, a description why this dependency is used.
     */
    @Nullable
    String getReason();

    /**
     * Returns a copy of this dependency with the given selection reason.
     */
    DependencyMetadata withReason(String reason);
}
