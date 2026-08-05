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

import com.google.common.base.Joiner;
import org.gradle.api.Describable;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.ResolvableSelectorState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.RejectedByAttributesVersion;
import org.gradle.internal.resolve.RejectedByRuleVersion;
import org.gradle.internal.resolve.RejectedBySelectorVersion;
import org.gradle.internal.resolve.RejectedVersion;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.ComponentIdResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentIdResolveResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.gradle.util.internal.TextUtil.getPluralEnding;

/**
 * Resolution state for a given component selector.
 * <p>
 * There are 3 possible states:
 * 1. The selector has been newly added to a `ModuleResolveState`. In this case {@link #requiresSelection} will be `true`.
 * 2. The selector failed to resolve. In this case {@link #failure} will be `!= null`.
 * 3. The selector was part of resolution to a particular component.
 * <p>
 * In this case {@link #requiresSelection} will be `false` and {@link ModuleResolveState#getSelected()} will point to the selected component.
 */
class SelectorState implements DependencyGraphSelector, ResolvableSelectorState {

    private final ComponentSelector componentSelector;
    private final DependencyToComponentIdResolver resolver;
    private final ResolveState resolveState;
    private final ResolvedVersionConstraint versionConstraint;
    private final boolean versionByAncestor;
    private final boolean isProjectSelector;

    private @Nullable ComponentIdResolveResult preferResult;
    private @Nullable ComponentIdResolveResult requireResult;
    private @Nullable ModuleVersionResolveException failure;
    private ModuleResolveState targetModule;

    /**
     * When true, the target module's current selection does not yet account for this selector's
     * state, and selection must run again before this selector's opinion is reflected in the graph.
     */
    private boolean requiresSelection = true;

    private boolean reusable;
    private boolean markedReusableAlready;

    // Counters used to track accumulated state of all outgoing edges that use this selector.
    // Since a ModuleResolveState tracks all selectors targeting itself, when considering
    // selectors that need to be used when choosing a version, a module must only consider
    // the selectors that currently have outgoing edges pointing to it. If not, then it means
    // the module was evicted, but it can still be reintegrated later in a different path.
    private int outgoingEdgeCount;
    private int outgoingConstraintEdgeCount;
    private int hardForcingEdgeCount;
    private int softForcingEdgeCount;
    private int lockingEdgeCount;
    private int changingEdgeCount;

    private @Nullable IvyArtifactName firstDependencyArtifact;

    SelectorState(ComponentSelector componentSelector, DependencyToComponentIdResolver resolver, ResolveState resolveState, ModuleIdentifier targetModuleId, boolean versionByAncestor) {
        this.resolver = resolver;
        this.resolveState = resolveState;
        this.targetModule = resolveState.getModule(targetModuleId);
        this.versionByAncestor = versionByAncestor;
        this.componentSelector = componentSelector;
        this.versionConstraint = versionByAncestor ?
            resolveState.resolveVersionConstraint(DefaultImmutableVersionConstraint.of()) :
            resolveState.resolveVersionConstraint(componentSelector);
        this.isProjectSelector = componentSelector instanceof ProjectComponentSelector;
    }

    @Override
    public boolean isProject() {
        // this is cached because used very often in sorting selectors
        return isProjectSelector;
    }

    /**
     * Register an edge that uses this selector. Contributions are reference counted
     * so that if the edge leaves the graph, their contributions to this selector can
     * be reverted.
     */
    public void use(EdgeState edge, boolean deferSelection) {
        if (edge.isConstraint()) {
            outgoingConstraintEdgeCount++;
            if (outgoingConstraintEdgeCount == 1) {
                targetModule.invalidateMergedConstraintAttributes();
            }
        }

        if (edge.isHardForcing()) {
            hardForcingEdgeCount++;
            if (hardForcingEdgeCount == 1) {
                this.requiresSelection = true;
            }
        }

        if (edge.isSoftForcing()) {
            softForcingEdgeCount++;
            if (softForcingEdgeCount == 1) {
                targetModule.resolveOptimizations.declareForcedPlatformInUse();
                this.requiresSelection = true;
            }
        }

        if (edge.isFromLock()) {
            lockingEdgeCount++;
            if (lockingEdgeCount == 1) {
                this.requiresSelection = true;
            }
        }

        if (edge.getDependencyMetadata().isChanging()) {
            changingEdgeCount++;
        }

        // TODO: The first dependency artifact is not reference counted, since it is arbitrary anyway.
        // We should fix this at some point.
        if (this.firstDependencyArtifact == null) {
            List<IvyArtifactName> artifacts = edge.getDependencyArtifacts();
            this.firstDependencyArtifact = artifacts.isEmpty() ? null : artifacts.get(0);
        }

        outgoingEdgeCount++;
        if (outgoingEdgeCount == 1) {
            // Register with the target module last, since the module orders its selectors
            // based on the state contributed above, (locking and forcing).
            targetModule.addSelector(this, deferSelection);
        }
    }

    /**
     * Decrease the count of edges using this selector, subtracting the state the released
     * edge contributed to this selector and updating the state on the target module if this
     * selector is no longer used by any edges.
     *
     * @return True if releasing this selector requires the target module to be reselected.
     */
    public boolean release(EdgeState edge) {
        boolean needsSelection = false;

        if (edge.isConstraint()) {
            outgoingConstraintEdgeCount--;
            assert outgoingConstraintEdgeCount >= 0 : "Inconsistent selector state detected for '" + this + "': outgoing constraint edge count cannot be negative";
            if (outgoingConstraintEdgeCount == 0) {
                targetModule.invalidateMergedConstraintAttributes();
            }
        }

        if (edge.isHardForcing()) {
            hardForcingEdgeCount--;
            assert hardForcingEdgeCount >= 0 : "Inconsistent selector state detected for '" + this + "': hard forcing edge count cannot be negative";
            if (hardForcingEdgeCount == 0) {
                this.requiresSelection = true;
                needsSelection = true;
            }
        }

        if (edge.isSoftForcing()) {
            softForcingEdgeCount--;
            assert softForcingEdgeCount >= 0 : "Inconsistent selector state detected for '" + this + "': soft forcing edge count cannot be negative";
            if (softForcingEdgeCount == 0) {
                this.requiresSelection = true;
                needsSelection = true;
            }
        }

        if (edge.isFromLock()) {
            lockingEdgeCount--;
            assert lockingEdgeCount >= 0 : "Inconsistent selector state detected for '" + this + "': locking edge count cannot be negative";
            if (lockingEdgeCount == 0) {
                this.requiresSelection = true;
                needsSelection = true;
            }
        }

        if (edge.getDependencyMetadata().isChanging()) {
            changingEdgeCount--;
            assert changingEdgeCount >= 0 : "Inconsistent selector state detected for '" + this + "': changing edge count cannot be negative";
        }

        outgoingEdgeCount--;
        assert outgoingEdgeCount >= 0 : "Inconsistent selector state detected for '" + this + "': outgoing edge count cannot be negative";
        if (outgoingEdgeCount == 0) {
            targetModule.removeSelector(this);
            needsSelection |= markForReuse();
            this.requiresSelection = true;
        }

        return needsSelection;
    }

    /**
     * True if this selector is currently used by at least one constraint edge.
     */
    boolean isUsedByConstraint() {
        return outgoingConstraintEdgeCount > 0;
    }

    @Override
    public String toString() {
        return componentSelector.toString();
    }

    public ModuleResolveState getTargetModule() {
        return targetModule;
    }

    /**
     * Return any failure to resolve the component selector to id, or failure to resolve component metadata for id.
     */
    @Nullable
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

    private ComponentIdResolveResult resolve(@Nullable VersionSelector selector, VersionSelector rejector, ComponentIdResolveResult previousResult) {
        try {
            if (!requiresResolve(previousResult, rejector)) {
                return previousResult;
            }

            BuildableComponentIdResolveResult idResolveResult = new DefaultBuildableComponentIdResolveResult();
            ComponentOverrideMetadata overrideMetadata = DefaultComponentOverrideMetadata.forDependency(isChanging(), firstDependencyArtifact);
            ImmutableAttributes requestAttributes = resolveState.getAttributesFactory().concat(resolveState.getConsumerAttributes(), targetModule.getMergedConstraintAttributes());
            resolver.resolve(componentSelector, overrideMetadata, selector, rejector, idResolveResult, requestAttributes);

            if (idResolveResult.getFailure() != null) {
                failure = idResolveResult.getFailure();
            }

            return idResolveResult;
        } finally {
            this.requiresSelection = false;
        }
    }

    private boolean requiresResolve(@Nullable ComponentIdResolveResult previousResult, @Nullable VersionSelector allRejects) {
        this.reusable = false;
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
        return allRejects != null && allRejects.accept(previousResult.getModuleVersionId().getVersion());
    }

    @Override
    public void markSelectionCompleted() {
        this.requiresSelection = false;
    }

    public boolean requiresSelection() {
        return requiresSelection;
    }

    /**
     * Marks a selector for reuse,
     * indicating it could be used again for resolution
     *
     * @return true if marking this selector for reuse requires the target module to be reselected
     */
    boolean markForReuse() {
        if (requiresSelection) {
            // Selector was marked for deferred selection - let's not trigger selection now
            return false;
        }
        this.reusable = true;
        if (markedReusableAlready) {
            // TODO: We have hit an unstable graph. This selector has already added, removed, added again,
            // and we are removing it once again. We should fail the resolution here and ask the user
            // to fix the graph -- likely by adding a version constraint.
            return false;
        } else {
            markedReusableAlready = true;
            return true;
        }
    }

    /**
     * Checks if the selector affects selection at the moment it is added to a module
     *
     * @return {@code true} if the selector can resolve, {@code false} otherwise
     */
    boolean canAffectSelection() {
        if (reusable) {
            return true;
        }
        return requiresSelection;
    }

    /**
     * Overrides the component that is the chosen for this selector.
     * This happens when the `ModuleResolveState` is restarted, during conflict resolution or version range merging.
     */
    public void overrideSelection(ComponentState selected) {
        this.requiresSelection = false;
        this.reusable = false;

        // Target module can change, if this is called as the result of a module or capability replacement conflict.
        this.targetModule = selected.getModule();
    }

    public boolean isVersionProvidedByAncestor() {
        return versionByAncestor;
    }

    public void visitSelectionReasons(Consumer<ComponentSelectionDescriptorInternal> visitor) {
        ComponentIdResolveResult result = getResult();
        if (result != null) {
            for (RejectedVersion rejectedVersion : result.getRejectedVersions()) {
                String version = rejectedVersion.getId().getVersion();
                if (rejectedVersion instanceof RejectedByRuleVersion) {
                    String reason = ((RejectedByRuleVersion) rejectedVersion).getReason();
                    visitor.accept(ComponentSelectionReasons.REJECTION.withDescription(new RejectedByRuleReason(version, reason)));
                } else if (rejectedVersion instanceof RejectedByAttributesVersion) {
                    visitor.accept(ComponentSelectionReasons.REJECTION.withDescription(new RejectedByAttributesReason((RejectedByAttributesVersion) rejectedVersion)));
                }
            }
        }
    }

    /**
     * Add additional details to the given reason descriptor, including any 'unmatched' or 'rejected' reasons.
     */
    public ComponentSelectionDescriptorInternal maybeEnhanceReason(ComponentSelectionDescriptorInternal descriptor) {
        ComponentIdResolveResult result = getResult();
        if (result == null) {
            return descriptor;
        }

        Collection<RejectedVersion> rejectedVersions = result.getRejectedVersions();
        if (!rejectedVersions.isEmpty()) {
            List<String> rejectedBySelector = null;
            for (RejectedVersion rejectedVersion : rejectedVersions) {
                if (rejectedVersion instanceof RejectedBySelectorVersion) {
                    if (rejectedBySelector == null) {
                        rejectedBySelector = new ArrayList<>(rejectedVersions.size());
                    }
                    rejectedBySelector.add(rejectedVersion.getId().getVersion());
                }
            }
            if (rejectedBySelector != null) {
                return descriptor.withDescription(new RejectedBySelectorReason(rejectedBySelector, descriptor));
            }
        }

        Set<String> unmatchedVersions = result.getUnmatchedVersions();
        if (!unmatchedVersions.isEmpty()) {
            return descriptor.withDescription(new UnmatchedVersionsReason(unmatchedVersions, descriptor));
        }

        return descriptor;
    }

    private @Nullable ComponentIdResolveResult getResult() {
        if (preferResult == null) {
            return requireResult;
        } else {
            return preferResult;
        }
    }

    @Override
    public IvyArtifactName getFirstDependencyArtifact() {
        return firstDependencyArtifact;
    }

    @Override
    public boolean isChanging() {
        return changingEdgeCount > 0;
    }

    @Override
    @Nullable
    public ResolvedVersionConstraint getVersionConstraint() {
        return versionConstraint;
    }

    @Override
    public ComponentSelector getComponentSelector() {
        return componentSelector;
    }

    @Override
    public boolean isForce() {
        return hardForcingEdgeCount > 0 || softForcingEdgeCount > 0;
    }

    @Override
    public boolean isSoftForce() {
        return softForcingEdgeCount > 0 && hardForcingEdgeCount == 0;
    }

    @Override
    public boolean isFromLock() {
        return lockingEdgeCount > 0;
    }

    @Override
    public boolean hasStrongOpinion() {
        return isForce() || (versionConstraint != null && versionConstraint.isStrict());
    }

    private static class UnmatchedVersionsReason implements Describable {

        private final Set<String> rejectedVersions;
        private final ComponentSelectionDescriptorInternal descriptor;

        private final int hashCode;

        public UnmatchedVersionsReason(Set<String> rejectedVersions, ComponentSelectionDescriptorInternal descriptor) {
            this.rejectedVersions = rejectedVersions;
            this.descriptor = descriptor;

            this.hashCode = computeHashCode(descriptor, rejectedVersions);
        }

        @Override
        public String getDisplayName() {
            boolean hasCustomDescription = descriptor.hasCustomDescription();
            StringBuilder sb = new StringBuilder(estimateSize(hasCustomDescription));
            sb.append("didn't match version").append(getPluralEnding(rejectedVersions)).append(" ");
            Joiner.on(", ").appendTo(sb, rejectedVersions);
            if (hasCustomDescription) {
                sb.append(" because ").append(descriptor.getDescription());
            }
            return sb.toString();
        }

        private int estimateSize(boolean hasCustomDescription) {
            return 24 + rejectedVersions.size() * 8 + (hasCustomDescription ? 24 : 0);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            UnmatchedVersionsReason that = (UnmatchedVersionsReason) o;
            return rejectedVersions.equals(that.rejectedVersions) &&
                descriptor.equals(that.descriptor);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        private static int computeHashCode(ComponentSelectionDescriptorInternal descriptor, Set<String> rejectedVersions) {
            int result = rejectedVersions.hashCode();
            result = 31 * result + descriptor.hashCode();
            return result;
        }

    }

    private static class RejectedByRuleReason implements Describable {
        private final String version;
        private final String reason;

        private RejectedByRuleReason(String version, @Nullable String reason) {
            this.version = version;
            this.reason = reason;
        }

        @Override
        public String getDisplayName() {
            return version + " by rule" + (reason != null ? " because " + reason : "");
        }
    }

    private static class RejectedByAttributesReason implements Describable {
        private final RejectedByAttributesVersion version;

        private RejectedByAttributesReason(RejectedByAttributesVersion version) {
            this.version = version;
        }

        @Override
        public String getDisplayName() {
            TreeFormatter formatter = new TreeFormatter();
            version.describeTo(formatter);
            return "version " + formatter;
        }
    }

    private static class RejectedBySelectorReason implements Describable {

        private final List<String> rejectedVersions;
        private final ComponentSelectionDescriptorInternal descriptor;

        private final int hashCode;

        public RejectedBySelectorReason(List<String> rejectedVersions, ComponentSelectionDescriptorInternal descriptor) {
            this.rejectedVersions = rejectedVersions;
            this.descriptor = descriptor;

            this.hashCode = computeHashCode(descriptor, rejectedVersions);
        }

        @Override
        public String getDisplayName() {
            boolean hasCustomDescription = descriptor.hasCustomDescription();
            StringBuilder sb = new StringBuilder(estimateSize(hasCustomDescription));
            sb.append("rejected version").append(getPluralEnding(rejectedVersions)).append(" ");
            Joiner.on(", ").appendTo(sb, rejectedVersions);
            if (hasCustomDescription) {
                sb.append(" because ").append(descriptor.getDescription());
            }
            return sb.toString();
        }

        private int estimateSize(boolean hasCustomDescription) {
            return 20 + rejectedVersions.size() * 8 + (hasCustomDescription ? 24 : 0);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            RejectedBySelectorReason that = (RejectedBySelectorReason) o;
            return rejectedVersions.equals(that.rejectedVersions) &&
                descriptor.equals(that.descriptor);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        private static int computeHashCode(ComponentSelectionDescriptorInternal descriptor, List<String> rejectedVersions) {
            int result = rejectedVersions.hashCode();
            result = 31 * result + descriptor.hashCode();
            return result;
        }

    }

}
