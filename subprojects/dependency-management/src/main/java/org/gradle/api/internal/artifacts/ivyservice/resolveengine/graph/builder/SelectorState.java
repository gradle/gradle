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
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.ComponentIdResolveResult;
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
    private ModuleResolveState targetModule;
    private ComponentState selected;
    private BuildableComponentIdResolveResult idResolveResult;
    private ResolvedVersionConstraint versionConstraint;

    SelectorState(Long id, DependencyState dependencyState, DependencyToComponentIdResolver resolver, ResolveState resolveState, ModuleIdentifier targetModuleId) {
        this.id = id;
        this.dependencyState = dependencyState;
        this.dependencyMetadata = dependencyState.getDependency();
        this.resolver = resolver;
        this.resolveState = resolveState;
        this.targetModule = resolveState.getModule(targetModuleId);
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

    ModuleVersionResolveException getFailure() {
        return failure != null ? failure : selected.getFailure();
    }

    public ComponentSelectionReason getSelectionReason() {
        if (selected != null) {
            return selected.getSelectionReason();
        }
        return createReason();
    }

    public ComponentState getSelected() {
        return targetModule.getSelected();
    }

    public ModuleResolveState getSelectedModule() {
        return targetModule;
    }

    /**
     * @return The module version, or null if there is a failure to resolve this selector.
     */
    public ComponentState resolveModuleRevisionId() {
        if (selected != null) {
            return selected;
        }
        if (failure != null) {
            return null;
        }

        idResolveResult = new DefaultBuildableComponentIdResolveResult();
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
            return null;
        }

        selected = resolveState.getRevision(idResolveResult.getModuleVersionId());
        selected.selectedBy(this);
        selected.addCause(idResolveResult.getSelectionDescription());
        if (dependencyState.getRuleDescriptor() != null) {
            selected.addCause(dependencyState.getRuleDescriptor());
        }
        targetModule = selected.getModule();
        targetModule.addSelector(this);
        versionConstraint = idResolveResult.getResolvedVersionConstraint();

        return selected;
    }

    private ComponentSelectionReasonInternal createReason() {
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



    public void restart(ComponentState moduleRevision) {
        this.selected = moduleRevision;
        this.targetModule = moduleRevision.getModule();
        ComponentResolveMetadata metaData = moduleRevision.getMetaData();
        if (metaData != null) {
            this.idResolveResult.resolved(metaData);
        }
    }

    public void reset() {
        this.idResolveResult = null;
        this.selected = null;
        this.targetModule = null;
    }

    public DependencyMetadata getDependencyMetadata() {
        return dependencyMetadata;
    }

    public ComponentIdResolveResult getResolveResult() {
        return idResolveResult;
    }

    public ResolvedVersionConstraint getVersionConstraint() {
        return versionConstraint;
    }
}
