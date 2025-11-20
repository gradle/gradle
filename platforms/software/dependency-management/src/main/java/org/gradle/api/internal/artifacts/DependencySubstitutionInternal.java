/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.DependencyArtifactSelector;
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public interface DependencySubstitutionInternal extends DependencySubstitution {
    void useTarget(Object notation, ComponentSelectionDescriptor ruleDescriptor);

    /**
     * Get the user-configured target, if any. Null if the user did not configure a target,
     * and the requested target should be used.
     */
    @Nullable ComponentSelector getConfiguredTargetSelector();

    /**
     * Get all descriptors describing the reasons for any substitutions performed.
     * <p>
     * Non-null and non-empty if any substitutions were performed.
     * Null if no substitutions were performed.
     */
    @Nullable ImmutableList<ComponentSelectionDescriptorInternal> getRuleDescriptors();

    /**
     * Returns the user-configured artifact selectors, if any. Null if the user did not
     * configure any artifact selectors and the requested artifact selectors should be used.
     */
    @Nullable ImmutableList<DependencyArtifactSelector> getConfiguredArtifactSelectors();

    /**
     * Get the target of the dependency substitution.
     */
    default ComponentSelector getTarget() {
        return getConfiguredTargetSelector() != null ? getConfiguredTargetSelector() : getRequested();
    }
}
