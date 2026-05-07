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
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
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
 * Resolution state for a given module version selector.
 *
 * There are 3 possible states:
 * 1. The selector has been newly added to a `ModuleResolveState`. In this case {@link #resolved} will be `false`.
 * 2. The selector failed to resolve. In this case {@link #failure} will be `!= null`.
 * 3. The selector was part of resolution to a particular module version.
 * In this case {@link #resolved} will be `true` and {@link ModuleResolveState#getSelected()} will point to the selected component.
 */
class SelectorState implements DependencyGraphSelector, ResolvableSelectorState {

    private final DependencyState dependencyState;
    private final DependencyToComponentIdResolver resolver;
    private final ResolveState resolveState;
    private final ResolvedVersionConstraint versionConstraint;
    private final boolean versionByAncestor;
    private final boolean isProjectSelector;
    private final AttributeDesugaring attributeDesugaring;

    private @Nullable ComponentIdResolveResult preferResult;
    private @Nullable ComponentIdResolveResult requireResult;
    private @Nullable ModuleVersionResolveException failure;
    private ModuleResolveState targetModule;
    private boolean resolved;
    private boolean forced;
    private boolean softForced;
    private boolean fromLock;
    private boolean reusable;
    private boolean markedReusableAlready;
    private boolean changing;

    // An internal counter used to track the number of outgoing edges
    // that use this selector. Since a module resolve state tracks all selectors
    // for this module, when considering selectors that need to be used when
    // choosing a version, we must only consider the ones which currently have
    // outgoing edges pointing to them. If not, then it means the module was
    // evicted, but it can still be reintegrated later in a different path.
    private int outgoingEdgeCount;

    SelectorState(DependencyState dependencyState, DependencyToComponentIdResolver resolver, ResolveState resolveState, ModuleIdentifier targetModuleId, boolean versionByAncestor) {
        this.resolver = resolver;
        this.resolveState = resolveState;
        this.targetModule = resolveState.getModule(targetModuleId);
        this.versionByAncestor = versionByAncestor;
        update(dependencyState);
        this.dependencyState = dependencyState;
        this.versionConstraint = versionByAncestor ?
            resolveState.resolveVersionConstraint(DefaultImmutableVersionConstraint.of()) :
            resolveState.resolveVersionConstraint(dependencyState.getDependency().getSelector());
        this.isProjectSelector = getSelector() instanceof ProjectComponentSelector;
        this.attributeDesugaring = resolveState.getAttributeDesugaring();
    }

    @Override
    public boolean isProject() {
        // this is cached because used very often in sorting selectors
        return isProjectSelector;
    }

    public void use(boolean deferSelection) {
        outgoingEdgeCount++;
        if (outgoingEdgeCount == 1) {
            targetModule.addSelector(this, deferSelection);
        }
    }

    public void release() {
        outgoingEdgeCount--;
        assert outgoingEdgeCount >= 0 : "Inconsistent selector state detected for '" + this + "': outgoing edge count cannot be negative";
        if (outgoingEdgeCount == 0) {
            removeAndMarkSelectorForReuse();
        }
    }

    private void removeAndMarkSelectorForReuse() {
        targetModule.removeSelector(this);
        resolved = false;
    }

    @Override
    public String toString() {
        return dependencyState.getDependency().toString();
    }

    @Override
    public ComponentSelector getRequested() {
        return attributeDesugaring.desugarSelector(dependencyState.getRequested());
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
            if (dependencyState.getSubstitutionFailure() != null) {
                idResolveResult.failed(dependencyState.getSubstitutionFailure());
            } else {
                IvyArtifactName firstArtifact = getFirstDependencyArtifact();
                ComponentOverrideMetadata overrideMetadata = DefaultComponentOverrideMetadata.forDependency(changing, firstArtifact);
                ImmutableAttributes requestAttributes = resolveState.getAttributesFactory().concat(resolveState.getConsumerAttributes(), targetModule.getMergedConstraintAttributes());
                resolver.resolve(dependencyState.getDependency().getSelector(), overrideMetadata, selector, rejector, idResolveResult, requestAttributes);
            }

            if (idResolveResult.getFailure() != null) {
                failure = idResolveResult.getFailure();
            }

            return idResolveResult;
        } finally {
            this.resolved = true;
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
    public void markResolved() {
        this.resolved = true;
    }

    public boolean isResolved() {
        return resolved;
    }

    /**
     * Marks a selector for reuse,
     * indicating it could be used again for resolution
     *
     * @return {@code true} if that selector has been marked for reuse before, {@code false} otherwise
     */
    boolean markForReuse() {
        if (!resolved) {
            // Selector was marked for deferred selection - let's not trigger selection now
            return true;
        }
        this.reusable = true;
        if (markedReusableAlready) {
            // TODO: We have hit an unstable graph. This selector has already added, removed, added again,
            // and we are removing it once again. We should fail the resolution here and ask the user
            // to fix the graph -- likely by adding a version constraint.
            return true;
        } else {
            markedReusableAlready = true;
            return false;
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
        return !resolved;
    }

    /**
     * Overrides the component that is the chosen for this selector.
     * This happens when the `ModuleResolveState` is restarted, during conflict resolution or version range merging.
     */
    public void overrideSelection(ComponentState selected) {
        this.resolved = true;
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

    public DependencyMetadata getDependencyMetadata() {
        return dependencyState.getDependency();
    }

    @Override
    public IvyArtifactName getFirstDependencyArtifact() {
        List<IvyArtifactName> artifacts = dependencyState.getDependency().getArtifacts();
        return artifacts.isEmpty() ? null : artifacts.get(0);
    }

    @Override
    public boolean isChanging() {
        return changing;
    }

    @Override
    @Nullable
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
    public boolean isSoftForce() {
        return softForced;
    }

    @Override
    public boolean isFromLock() {
        return fromLock;
    }

    @Override
    public boolean hasStrongOpinion() {
        return forced || (versionConstraint != null && versionConstraint.isStrict());
    }

    public void update(DependencyState dependencyState) {
        if (dependencyState != this.dependencyState) {
            if (!forced && dependencyState.isForced()) {
                forced = true;
                if (dependencyState.getDependency() instanceof LenientPlatformDependencyMetadata) {
                    softForced = true;
                    targetModule.resolveOptimizations.declareForcedPlatformInUse();
                }
                resolved = false; // when a selector changes from non forced to forced, we must reselect
            }
            if (!fromLock && dependencyState.isFromLock()) {
                fromLock = true;
                resolved = false; // when a selector changes from non lock to lock, we must reselect
            }

            changing = changing || dependencyState.getDependency().isChanging();
        }
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
