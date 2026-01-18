/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ForcingDependencyMetadata;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.jspecify.annotations.Nullable;

/**
 * A declared dependency, potentially transformed based on a substitution.
 */
public class DependencyState {

    /**
     * The original requested component, before substitution.
     */
    private final ComponentSelector requested;

    /**
     * The declared dependency this state is based off of, after substitution.
     */
    private final DependencyMetadata dependency;

    /**
     * Describes the substitutions applied to this dependency, if any.
     */
    private final ImmutableList<ComponentSelectionDescriptorInternal> ruleDescriptors;

    /**
     * If non-null, the failure that occurred while trying to substitute this dependency.
     */
    private final @Nullable ModuleVersionResolveException substitutionFailure;

    private @Nullable ModuleIdentifier moduleIdentifier;

    public DependencyState(
        DependencyMetadata dependency,
        ComponentSelector requested,
        ImmutableList<ComponentSelectionDescriptorInternal> ruleDescriptors,
        @Nullable ModuleVersionResolveException substitutionFailure
    ) {
        this.dependency = dependency;
        this.requested = requested;
        this.ruleDescriptors = ruleDescriptors;
        this.substitutionFailure = substitutionFailure;
    }

    public ComponentSelector getRequested() {
        return requested;
    }

    public DependencyMetadata getDependency() {
        return dependency;
    }

    public ImmutableList<ComponentSelectionDescriptorInternal> getRuleDescriptors() {
        return ruleDescriptors;
    }

    public @Nullable ModuleVersionResolveException getSubstitutionFailure() {
        return substitutionFailure;
    }

    /**
     * Determine the module identifier of the component that this dependency targets.
     * <p>
     * This may resolve the target component. In practice all components do not necessarily belong
     * to a module, so we should avoid this method if possible. If possible, we should delay this
     * sort of functionality to _after_ we've resolved a selector to a component.
     */
    public ModuleIdentifier getModuleIdentifier(ComponentSelectorConverter componentSelectorConverter) {
        if (moduleIdentifier == null) {
            ComponentSelector componentSelector = dependency.getSelector();
            if (componentSelector instanceof ModuleComponentSelector) {
                moduleIdentifier = ((ModuleComponentSelector) componentSelector).getModuleIdentifier();
            } else {
                moduleIdentifier = componentSelectorConverter.getModuleVersionId(componentSelector).getModule();
            }
        }
        return moduleIdentifier;
    }

    public boolean isForced() {
        if (!ruleDescriptors.isEmpty()) {
            for (ComponentSelectionDescriptorInternal ruleDescriptor : ruleDescriptors) {
                if (ruleDescriptor.isEquivalentToForce()) {
                    return true;
                }
            }
        }

        return dependency instanceof ForcingDependencyMetadata && ((ForcingDependencyMetadata) dependency).isForce();
    }

    public boolean isFromLock() {
        return dependency instanceof LocalOriginDependencyMetadata && ((LocalOriginDependencyMetadata) dependency).isFromLock();
    }

    @Override
    public String toString() {
        if (requested.equals(dependency.getSelector())) {
            return dependency.toString();
        } else {
            return dependency + " (requested " + requested + ")";
        }
    }

}
