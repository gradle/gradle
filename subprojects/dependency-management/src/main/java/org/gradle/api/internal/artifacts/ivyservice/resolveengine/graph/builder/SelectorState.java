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
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentIdResolveResult;

import java.util.List;

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons.CONSTRAINT;

/**
 * Resolution state for a given module version selector.
 */
class SelectorState implements DependencyGraphSelector {
    private final Long id;
    private final DependencyState dependencyState;
    private final DependencyMetadata dependencyMetadata;
    private final DependencyToComponentIdResolver resolver;
    private final ResolveState resolveState;
    private ModuleVersionResolveException failure;
    private ComponentSelectionReasonInternal failureSelectionReason;
    private ModuleResolveState targetModule;
    private ComponentState selected;
    private ResolvedVersionConstraint versionConstraint;

    SelectorState(Long id, DependencyState dependencyState, DependencyToComponentIdResolver resolver, ResolveState resolveState, ModuleIdentifier targetModuleId) {
        this.id = id;
        this.dependencyState = dependencyState;
        this.dependencyMetadata = dependencyState.getDependency();
        this.resolver = resolver;
        this.resolveState = resolveState;
        this.targetModule = resolveState.getModule(targetModuleId);
        targetModule.addSelector(this);
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
     * On successful resolve, a `ComponentState` is constructed for the identifier, recorded as {@link #selected}, and returned.
     * On resolve failure, the failure is recorded and a `null` component is {@link #selected} and returned.
     * @return A component state for the selected component id, or null if there is a failure to resolve this selector.
     */
    public ComponentState resolveModuleRevisionId() {
        if (selected != null) {
            return selected;
        }
        if (failure != null) {
            return null;
        }

        BuildableComponentIdResolveResult idResolveResult = new DefaultBuildableComponentIdResolveResult();
        if (dependencyState.failure != null) {
            idResolveResult.failed(dependencyState.failure);
        } else {
            if (dependencyMetadata.isPending()) {
                idResolveResult.setSelectionDescription(CONSTRAINT);
            }
            resolver.resolve(dependencyMetadata, idResolveResult);
        }

        if (idResolveResult.getFailure() != null) {
            failure = idResolveResult.getFailure();
            failureSelectionReason = createFailureReason(idResolveResult);
            return null;
        }

        selected = resolveState.getRevision(idResolveResult.getId(), idResolveResult.getModuleVersionId());
        selected.selectedBy(this, idResolveResult);
        selected.addCause(idResolveResult.getSelectionDescription());
        if (dependencyState.getRuleDescriptor() != null) {
            selected.addCause(dependencyState.getRuleDescriptor());
        }
        versionConstraint = idResolveResult.getResolvedVersionConstraint();

        // We should never select a component for a different module, but the JVM software model dependency resolution is doing this.
        // TODO Ditch the JVM Software Model plugins and re-add this assertion
//        assert selected.getModule() == targetModule;

        return selected;
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

    private ComponentSelectionReasonInternal createFailureReason(BuildableComponentIdResolveResult idResolveResult) {
        // TODO:DAZ This duplicates logic in `resolveModuleRevisionId`, and could be simplified
        boolean hasRuleDescriptor = dependencyState.getRuleDescriptor() != null;
        boolean isConstraint = dependencyMetadata.isPending();
        ComponentSelectionDescriptorInternal description = idResolveResult.getSelectionDescription();
        if (!hasRuleDescriptor && !isConstraint) {
            return VersionSelectionReasons.of(description);
        }
        List<ComponentSelectionDescriptorInternal> descriptors = Lists.newArrayListWithCapacity(isConstraint && hasRuleDescriptor ? 3 : 2);
        descriptors.add(description);
        if (hasRuleDescriptor) {
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
