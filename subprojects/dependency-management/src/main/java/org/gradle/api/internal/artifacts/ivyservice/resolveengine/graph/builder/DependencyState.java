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

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.internal.Describables;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ForcingDependencyMetadata;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;

import java.util.List;
import java.util.Set;

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.*;

class DependencyState {
    private final ComponentSelector requested;
    private final DependencyMetadata dependency;
    private final List<ComponentSelectionDescriptorInternal> ruleDescriptors;
    private final ComponentSelectorConverter componentSelectorConverter;

    private ModuleIdentifier moduleIdentifier;
    public ModuleVersionResolveException failure;

    DependencyState(DependencyMetadata dependency, ComponentSelectorConverter componentSelectorConverter) {
        this(dependency, dependency.getSelector(), null, componentSelectorConverter);
    }

    private DependencyState(DependencyMetadata dependency, ComponentSelector requested, List<ComponentSelectionDescriptorInternal> ruleDescriptors, ComponentSelectorConverter componentSelectorConverter) {
        this.dependency = dependency;
        this.requested = requested;
        this.ruleDescriptors = ruleDescriptors;
        this.componentSelectorConverter = componentSelectorConverter;
    }

    public ComponentSelector getRequested() {
        return requested;
    }

    public DependencyMetadata getDependency() {
        return dependency;
    }

    public ModuleIdentifier getModuleIdentifier() {
        if (moduleIdentifier == null) {
            moduleIdentifier = componentSelectorConverter.getModule(dependency.getSelector());
        }
        return moduleIdentifier;
    }

    public DependencyState withTarget(ComponentSelector target, List<ComponentSelectionDescriptorInternal> ruleDescriptors) {
        DependencyMetadata targeted = dependency.withTarget(target);
        return new DependencyState(targeted, requested, ruleDescriptors, componentSelectorConverter);
    }

    /**
     * Descriptor for any rules that modify this DependencyState from the original.
     */
    public List<ComponentSelectionDescriptorInternal> getRuleDescriptors() {
        return ruleDescriptors;
    }

    public boolean isForced() {
        if (ruleDescriptors != null) {
            for (ComponentSelectionDescriptorInternal ruleDescriptor : ruleDescriptors) {
                if (ruleDescriptor.isEquivalentToForce()) {
                    return true;
                }
            }
        }
        return isDependencyForced();
    }

    private boolean isDependencyForced() {
        return dependency instanceof ForcingDependencyMetadata && ((ForcingDependencyMetadata) dependency).isForce();
    }

    public boolean isFromLock() {
        return dependency instanceof LocalOriginDependencyMetadata && ((LocalOriginDependencyMetadata) dependency).isFromLock();
    }

    public void addSelectionReasons(Set<ComponentSelectionDescriptorInternal> reasons) {
        String reason = dependency.getReason();
        ComponentSelectionDescriptorInternal dependencyDescriptor = dependency.isConstraint() ? CONSTRAINT : REQUESTED;
        if (reason != null) {
            dependencyDescriptor = dependencyDescriptor.withDescription(Describables.of(reason));
        }
        reasons.add(dependencyDescriptor);

        if (ruleDescriptors != null) {
            reasons.addAll(ruleDescriptors);
        }
        if (isDependencyForced()) {
            reasons.add(FORCED);
        }
    }
}
