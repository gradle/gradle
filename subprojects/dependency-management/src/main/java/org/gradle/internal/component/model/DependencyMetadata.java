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

import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;

import java.util.Set;

public interface DependencyMetadata {
    ModuleVersionSelector getRequested();

    /**
     * Returns the artifacts referenced by this dependency for the given combination of source and target configurations, if any. Returns an empty set if
     * this dependency does not reference any specific artifacts - the defaults for the target configuration should be used in this case.
     */
    // TODO:ADAM - fromConfiguration should be implicit in this metadata
    Set<ComponentArtifactMetadata> getArtifacts(ConfigurationMetadata fromConfiguration, ConfigurationMetadata toConfiguration);

    /**
     * Returns the artifacts referenced by this dependency, if any. Returns an empty set if this dependency does not reference any specific artifacts - the
     * defaults should be used in this case.
     */
    Set<IvyArtifactName> getArtifacts();

    /**
     * Returns a copy of this dependency with the given requested version.
     */
    DependencyMetadata withRequestedVersion(String requestedVersion);

    /**
     * Returns a copy of this dependency with the given target.
     */
    DependencyMetadata withTarget(ComponentSelector target);

    /**
     * Returns the component selector for this dependency.
     *
     * @return Component selector
     */
    ComponentSelector getSelector();

    /**
     * Returns the exclusions to apply when traversing this dependency from the given configuration
     */
    // TODO:ADAM - fromConfiguration should be implicit in this metadata
    ModuleExclusion getExclusions(ConfigurationMetadata fromConfiguration);

    /**
     * Select the target configurations for this dependency from the given target component.
     */
    // TODO:ADAM - fromComponent and fromConfiguration should be implicit in this metadata
    Set<ConfigurationMetadata> selectConfigurations(ComponentResolveMetadata fromComponent, ConfigurationMetadata fromConfiguration, ComponentResolveMetadata targetComponent, AttributesSchema attributesSchema);

    /**
     * Returns the set of source configurations that this dependency should be attached to.
     */
    Set<String> getModuleConfigurations();

    boolean isChanging();

    boolean isTransitive();

    boolean isForce();

    String getDynamicConstraintVersion();

}
