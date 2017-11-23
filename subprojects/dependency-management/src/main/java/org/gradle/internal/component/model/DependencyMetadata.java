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
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import java.util.List;
import java.util.Set;

public interface DependencyMetadata {
    /**
     * Returns the artifacts referenced by this dependency, if any.
     * When a dependency references artifacts, those artifacts are used in place of the default artifacts of the target component.
     * In most cases, it makes sense for this set to be empty, and for all of the artifacts of the target component to be included.
     */
    Set<IvyArtifactName> getArtifacts();

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
     * Returns a view of the excludes filtered for this dependency in this configuration.
     */
    List<Exclude> getExcludes();

    /**
     * Select the target configurations for this dependency from the given target component.
     */
    Set<ConfigurationMetadata> selectConfigurations(ImmutableAttributes consumerAttributes, ComponentResolveMetadata targetComponent, AttributesSchemaInternal consumerSchema);

    boolean isChanging();

    boolean isTransitive();

    boolean isForce();

    boolean isOptional();
}
