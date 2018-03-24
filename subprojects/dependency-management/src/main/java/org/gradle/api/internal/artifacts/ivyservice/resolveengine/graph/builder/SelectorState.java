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

import com.google.common.collect.Lists;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.ComponentIdResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentIdResolveResult;

import java.util.List;

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons.CONSTRAINT;
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons.REQUESTED;

/**
 * Resolution state for a given module version selector.
 */
class SelectorState implements DependencyGraphSelector {
    // TODO:DAZ Should inject this
    private static final VersionSelectorScheme VERSION_SELECTOR_SCHEME = new DefaultVersionSelectorScheme(new DefaultVersionComparator());
    private final Long id;
    private final DependencyState dependencyState;
    private final DependencyMetadata dependencyMetadata;
    private final DependencyToComponentIdResolver resolver;
    private final ResolvedVersionConstraint versionConstraint;

    private ComponentIdResolveResult idResolveResult;
    private ModuleVersionResolveException failure;
    private ComponentSelectionReasonInternal failureSelectionReason;
    private ModuleResolveState targetModule;
    ComponentState selected;

    SelectorState(Long id, DependencyState dependencyState, DependencyToComponentIdResolver resolver, ResolveState resolveState, ModuleIdentifier targetModuleId) {
        this.id = id;
        this.dependencyState = dependencyState;
        this.dependencyMetadata = dependencyState.getDependency();
        this.resolver = resolver;
        this.targetModule = resolveState.getModule(targetModuleId);
        this.versionConstraint = resolveVersionConstraint(dependencyMetadata.getSelector());
        targetModule.addSelector(this);
    }

    private ResolvedVersionConstraint resolveVersionConstraint(ComponentSelector selector) {
        if (selector instanceof ModuleComponentSelector) {
            return new DefaultResolvedVersionConstraint(((ModuleComponentSelector) selector).getVersionConstraint(), VERSION_SELECTOR_SCHEME);
        }
        return null;
    }

    @Override
    public Long getResultId() {
        return id;
    }

    @Override
    public String toString() {
        return dependencyMetadata.toString();
    }

    @Override
    public ComponentSelector getRequested() {
        return dependencyState.getRequested();
    }

    public ModuleResolveState getTargetModule() {
        return targetModule;
    }

    /**
     * Return any failure to resolve the component selector to id, or failure to resolve component metadata for id.
     */
    ModuleVersionResolveException getFailure() {
        return failure != null ? failure : selected.getFailure();
    }

    /**
     * The component that was actually chosen for this component selector.
     */
    public ComponentState getSelected() {
        return targetModule.getSelected();
    }

    /**
     * Does the work of actually resolving a component selector to a component identifier.
     */
    public ComponentIdResolveResult resolve() {
        if (idResolveResult != null) {
            return idResolveResult;
        }

        BuildableComponentIdResolveResult idResolveResult = new DefaultBuildableComponentIdResolveResult();
        if (dependencyState.failure != null) {
            idResolveResult.failed(dependencyState.failure);
        } else {
            resolver.resolve(dependencyMetadata, versionConstraint, idResolveResult);
        }

        if (idResolveResult.getFailure() != null) {
            failure = idResolveResult.getFailure();
            failureSelectionReason = getReasonForSelector();
        }

        this.idResolveResult = idResolveResult;
        return idResolveResult;
    }

    public void select(ComponentState selected) {
        selected.selectedBy(this);
        ComponentSelectionDescriptorInternal selectionDescriptor = selectionDescriptionForDependency(dependencyMetadata);
        selected.addCause(selectionDescriptor);
        if (dependencyState.getRuleDescriptor() != null) {
            selected.addCause(dependencyState.getRuleDescriptor());
        }

        // We should never select a component for a different module, but the JVM software model dependency resolution is doing this.
        // TODO Ditch the JVM Software Model plugins and re-add this assertion
//        assert selected.getModule() == targetModule;

        this.selected = selected;
    }

    private ComponentSelectionDescriptorInternal selectionDescriptionForDependency(DependencyMetadata dependencyMetadata) {
        ComponentSelectionDescriptorInternal selectionDescriptor = dependencyMetadata.isPending() ? CONSTRAINT : REQUESTED;
        if (dependencyMetadata.getReason() != null) {
            selectionDescriptor = selectionDescriptor.withReason(dependencyMetadata.getReason());
        }
        return selectionDescriptor;
    }

    public ComponentSelectionReason getSelectionReason() {
        if (selected != null) {
            // For successful selection, the selected component provides the reason.
            return selected.getSelectionReason();
        }
        // Create a reason in case of selection failure.
        assert failure != null;
        assert failureSelectionReason != null;
        return failureSelectionReason;
    }

    public ComponentSelectionReasonInternal getReasonForSelector() {
        List<ComponentSelectionDescriptorInternal> descriptors = Lists.newArrayListWithCapacity(2);
        descriptors.add(selectionDescriptionForDependency(dependencyMetadata));
        if (dependencyState.getRuleDescriptor() != null) {
            descriptors.add(dependencyState.getRuleDescriptor());
        }
        return VersionSelectionReasons.of(descriptors);
    }

    /**
     * Overrides the component that is the chosen for this selector.
     * This happens when the `ModuleResolveState` is restarted, during conflict resolution or 'softSelect' with version range merging.
     */
    public void overrideSelection(ComponentState selectedComponent) {
        this.selected = selectedComponent;

        // Target module can change, if this is called as the result of a module replacement conflict.
        // TODO:DAZ We are not updating the set of selectors for the updated module (or for the module that the selectors were removed from)
        this.targetModule = selectedComponent.getModule();
    }

    public DependencyMetadata getDependencyMetadata() {
        return dependencyMetadata;
    }

    public ResolvedVersionConstraint getVersionConstraint() {
        return versionConstraint;
    }
}
