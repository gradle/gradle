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

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentSelector;

import java.util.Set;

public interface DependencyMetaData {
    ModuleVersionSelector getRequested();

    /**
     * Returns this dependency as an Ivy DependencyDescriptor. This method is here to allow us to migrate away from the Ivy types
     * and will be removed.
     *
     * <p>You should avoid using this method.
     */
    DependencyDescriptor getDescriptor();

    boolean isChanging();

    boolean isTransitive();

    /**
     * Returns the artifacts referenced by this dependency for the given combination of source and target configurations, if any. Returns an empty set if
     * this dependency does not reference any specific artifacts - the defaults for the target configuration should be used in this case.
     */
    // TODO:ADAM - fromConfiguration should be implicit in this metadata
    Set<ComponentArtifactMetaData> getArtifacts(ConfigurationMetaData fromConfiguration, ConfigurationMetaData toConfiguration);

    /**
     * Returns the artifacts referenced by this dependency, if any. Returns an empty set if this dependency does not reference any specific artifacts - the
     * defaults should be used in this case.
     */
    Set<IvyArtifactName> getArtifacts();

    /**
     * Returns a copy of this dependency with the given requested version.
     */
    DependencyMetaData withRequestedVersion(String requestedVersion);

    /**
     * Returns a copy of this dependency with the given requested version.
     */
    DependencyMetaData withRequestedVersion(ModuleVersionSelector requestedVersion);

    /**
     * Returns a copy of this dependency with the changing flag set.
     */
    DependencyMetaData withChanging();

    /**
     * Returns the component selector for this dependency.
     *
     * @return Component selector
     */
    ComponentSelector getSelector();
}
