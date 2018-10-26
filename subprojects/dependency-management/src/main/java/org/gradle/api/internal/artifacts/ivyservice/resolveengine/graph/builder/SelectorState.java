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

import com.google.common.collect.Sets;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.ResolvableSelectorState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.ComponentIdResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentIdResolveResult;

import java.util.Set;

/**
 * Resolution state for a given module version selector.
 *
 * There are 3 possible states:
 * 1. The selector has been newly added to a `ModuleResolveState`. In this case {@link #resolved} will be `false`.
 * 2. The selector failed to resolve. In this case {@link #failure} will be `!= null`.
 * 3. The selector was part of resolution to a particular module version.
 * In this case {@link #resolved} will be `true` and {@link ModuleResolveState#getSelected()} will point to the selected component.
 */
class SelectorState implements DependencyGraphSelector, ResolvableSelectorState {
    private static final Transformer<ComponentSelectionDescriptorInternal, ComponentSelectionDescriptorInternal> IDENTITY = new Transformer<ComponentSelectionDescriptorInternal, ComponentSelectionDescriptorInternal>() {
        @Override
        public ComponentSelectionDescriptorInternal transform(ComponentSelectionDescriptorInternal componentSelectionDescriptorInternal) {
            return componentSelectionDescriptorInternal;
        }
    };
    private final Long id;
    private final DependencyState dependencyState;
    private final DependencyMetadata firstSeenDependency;
    private final DependencyToComponentIdResolver resolver;
    private final DefaultResolvedVersionConstraint versionConstraint;
    private final VersionSelectorScheme versionSelectorScheme;
    private final ImmutableAttributesFactory attributesFactory;
    private final Set<ComponentSelectionDescriptorInternal> dependencyReasons = Sets.newLinkedHashSet();

    private ComponentIdResolveResult preferResult;
    private ComponentIdResolveResult requireResult;
    private ModuleVersionResolveException failure;
    private ModuleResolveState targetModule;
    private boolean resolved;
    private boolean forced;
    private boolean fromLock;

    // An internal counter used to track the number of outgoing edges
    // that use this selector. Since a module resolve state tracks all selectors
    // for this module, when considering selectors that need to be used when
    // choosing a version, we must only consider the ones which currently have
    // outgoing edges pointing to them. If not, then it means the module was
    // evicted, but it can still be reintegrated later in a different path.
    private int outgoingEdgeCount;

    SelectorState(Long id, DependencyState dependencyState, DependencyToComponentIdResolver resolver, VersionSelectorScheme versionSelectorScheme, ResolveState resolveState, ModuleIdentifier targetModuleId) {
        this.id = id;
        this.resolver = resolver;
        this.versionSelectorScheme = versionSelectorScheme;
        this.targetModule = resolveState.getModule(targetModuleId);
        this.attributesFactory = resolveState.getAttributesFactory();

        update(dependencyState);
        this.dependencyState = dependencyState;
        this.firstSeenDependency = dependencyState.getDependency();
        this.versionConstraint = resolveVersionConstraint(firstSeenDependency.getSelector());
    }

    public void use() {
        outgoingEdgeCount++;
        if (outgoingEdgeCount == 1) {
            targetModule.addSelector(this);
        }
    }

    public void release() {
        outgoingEdgeCount--;
        assert outgoingEdgeCount >= 0 : "Inconsistent selector state detected: outgoing edge count cannot be negative";
        if (outgoingEdgeCount == 0) {
            removeAndMarkSelectorForReuse();
        }
    }

    private void removeAndMarkSelectorForReuse() {
        targetModule.removeSelector(this);
        resolved = false;
    }

    private DefaultResolvedVersionConstraint resolveVersionConstraint(ComponentSelector selector) {
        if (selector instanceof ModuleComponentSelector) {
            return new DefaultResolvedVersionConstraint(((ModuleComponentSelector) selector).getVersionConstraint(), versionSelectorScheme);
        }
        return null;
    }

    @Override
    public Long getResultId() {
        return id;
    }

    @Override
    public String toString() {
        return firstSeenDependency.toString();
    }

    @Override
    public ComponentSelector getRequested() {
        return selectorWithDesugaredAttributes(dependencyState.getRequested());
    }

    public ModuleResolveState getTargetModule() {
        return targetModule;
    }

    /**
     * Return any failure to resolve the component selector to id, or failure to resolve component metadata for id.
     */
    ModuleVersionResolveException getFailure() {
        return failure;
    }

    /**
     * Does the work of actually resolving a component selector to a component identifier.
     */
    @Override
    public ComponentIdResolveResult resolve(VersionSelector allRejects) {
        VersionSelector requiredSelector = versionConstraint == null ? null : versionConstraint.getRequiredSelector();
        requireResult = resolve(requiredSelector, allRejects, requireResult);
        return requireResult;
    }

    @Override
    public ComponentIdResolveResult resolvePrefer(VersionSelector allRejects) {
        if (versionConstraint == null || versionConstraint.getPreferredSelector() == null) {
            return null;
        }
        preferResult = resolve(versionConstraint.getPreferredSelector(), allRejects, preferResult);
        return preferResult;
    }

    private ComponentIdResolveResult resolve(VersionSelector selector, VersionSelector rejector, ComponentIdResolveResult previousResult) {
        try {
            if (!requiresResolve(previousResult, rejector)) {
                return previousResult;
            }

            BuildableComponentIdResolveResult idResolveResult = new DefaultBuildableComponentIdResolveResult();
            if (dependencyState.failure != null) {
                idResolveResult.failed(dependencyState.failure);
            } else {
                resolver.resolve(firstSeenDependency, selector, rejector, idResolveResult);
            }

            if (idResolveResult.getFailure() != null) {
                failure = idResolveResult.getFailure();
            }

            return idResolveResult;
        } finally {
            this.resolved = true;
        }
    }

    @Override
    public void failed(ModuleVersionResolveException failure) {
        this.failure = failure;
        BuildableComponentIdResolveResult idResolveResult = new DefaultBuildableComponentIdResolveResult();
        idResolveResult.failed(failure);
        this.requireResult = idResolveResult;
        this.preferResult = idResolveResult;
    }

    private boolean requiresResolve(ComponentIdResolveResult previousResult, VersionSelector allRejects) {
        // If we've never resolved, must resolve
        if (previousResult == null) {
            return true;
        }

        // If previous resolve failed, no point in re-resolving
        if (previousResult.getFailure() != null) {
            return false;
        }

        // If the previous result was rejected, do not need to re-resolve (new rejects will be a superset of previous rejects)
        if (previousResult.isRejected()) {
            return false;
        }

        // If the previous result is still not rejected, do not need to re-resolve. The previous result is still good.
        if (allRejects == null || !allRejects.accept(previousResult.getModuleVersionId().getVersion())) {
            return false;
        }

        return true;
    }

    @Override
    public void markResolved() {
        this.resolved = true;
    }

    public boolean isResolved() {
        return resolved;
    }

    /**
     * Overrides the component that is the chosen for this selector.
     * This happens when the `ModuleResolveState` is restarted, during conflict resolution or version range merging.
     */
    public void overrideSelection(ComponentState selected) {
        this.resolved = true;

        // Target module can change, if this is called as the result of a module replacement conflict.
        this.targetModule = selected.getModule();
    }

    public ComponentSelectionReasonInternal getSelectionReason() {
        // Create a component selection reason specific to this selector.
        return addReasonsForSelector(ComponentSelectionReasons.empty(), IDENTITY);
    }

    public ComponentSelectionReasonInternal addReasonsForSelector(ComponentSelectionReasonInternal selectionReason, Transformer<ComponentSelectionDescriptorInternal, ComponentSelectionDescriptorInternal> transformer) {
        for (ComponentSelectionDescriptorInternal dependencyDescriptor : dependencyReasons) {
            selectionReason.addCause(transformer.transform(dependencyDescriptor));
        }
        return selectionReason;
    }

    public DependencyMetadata getDependencyMetadata() {
        return firstSeenDependency;
    }

    public ResolvedVersionConstraint getVersionConstraint() {
        return versionConstraint;
    }

    @Override
    public ComponentSelector getSelector() {
        return dependencyState.getDependency().getSelector();
    }

    @Override
    public boolean isForce() {
        return forced;
    }

    @Override
    public boolean isFromLock() {
        return fromLock;
    }

    private ComponentSelector selectorWithDesugaredAttributes(ComponentSelector selector) {
        return AttributeDesugaring.desugarSelector(selector, attributesFactory);
    }

    public void update(DependencyState dependencyState) {
        if (dependencyState != this.dependencyState) {
            if (!forced && dependencyState.isForced()) {
                forced = true;
                resolved = false; // when a selector changes from non forced to forced, we must reselect
            }
            if (!fromLock && dependencyState.isFromLock()) {
                fromLock = true;
                resolved = false; // when a selector changes from non lock to lock, we must reselect
            }
            dependencyState.addSelectionReasons(dependencyReasons);
        }
    }
}
