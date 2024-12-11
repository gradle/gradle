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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.ResolvableSelectorState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.gradle.util.internal.TextUtil.getPluralEnding;

/**
 * Resolution state for a given component selector.
 *
 * There are 3 possible states:
 * 1. The selector has been newly added to a `ModuleResolveState`. In this case {@link #resolved} will be `false`.
 * 2. The selector failed to resolve. In this case {@link #dependencyFailure} will be `!= null`, or either {@link #preferResult} or {@link #requireResult} will have a failure.
 * 3. The selector was part of resolution to a particular component.
 *
 * In this case {@link #resolved} will be `true` and {@link ModuleResolveState#getSelected()} will point to the selected component.
 */
class SelectorState implements DependencyGraphSelector, ResolvableSelectorState {
    private final ComponentSelector componentSelector;
    private final DependencyToComponentIdResolver resolver;
    private final ResolvedVersionConstraint versionConstraint;
    private final List<ComponentSelectionDescriptorInternal> dependencyReasons = new ArrayList<>(4);
    private final boolean isProjectSelector;

    private ComponentIdResolveResult preferResult;
    private ComponentIdResolveResult requireResult;
    private ModuleVersionResolveException dependencyFailure;
    private ModuleVersionResolveException failure;
    private ModuleResolveState targetModule;
    private boolean resolved;
    private boolean forced;
    private boolean softForced;
    private boolean fromLock;
    private boolean reusable;
    private boolean markedReusableAlready;
    private boolean constraint;

    @SuppressWarnings("deprecation")
    private org.gradle.api.artifacts.ClientModule clientModule;
    private boolean changing;
    private IvyArtifactName firstDependencyArtifact;

    // An internal counter used to track the number of outgoing edges
    // that use this selector. Since a module resolve state tracks all selectors
    // for this module, when considering selectors that need to be used when
    // choosing a version, we must only consider the ones which currently have
    // outgoing edges pointing to them. If not, then it means the module was
    // evicted, but it can still be reintegrated later in a different path.
    private int outgoingEdgeCount;

    SelectorState(
        ComponentSelector componentSelector,
        DependencyToComponentIdResolver resolver,
        ResolveState resolveState,
        ModuleResolveState targetModule,
        boolean versionByAncestor
    ) {
        this.componentSelector = componentSelector;
        this.resolver = resolver;
        this.targetModule = targetModule;
        this.isProjectSelector = componentSelector instanceof ProjectComponentSelector;

        if (versionByAncestor) {
            dependencyReasons.add(ComponentSelectionReasons.BY_ANCESTOR);
            this.versionConstraint = resolveState.resolveVersionConstraint(DefaultImmutableVersionConstraint.of());
        } else if (componentSelector instanceof ModuleComponentSelector) {
            this.versionConstraint = resolveState.resolveVersionConstraint(((ModuleComponentSelector) componentSelector).getVersionConstraint());
        } else {
            assert isProjectSelector;
            this.versionConstraint = null;
        }
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
        assert outgoingEdgeCount >= 0 : "Inconsistent selector state detected: outgoing edge count cannot be negative";
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
        return componentSelector.getDisplayName();
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

    private ComponentIdResolveResult resolve(@Nullable VersionSelector acceptor, VersionSelector rejector, ComponentIdResolveResult previousResult) {
        try {
            if (!requiresResolve(previousResult, rejector)) {
                return previousResult;
            }

            BuildableComponentIdResolveResult idResolveResult = new DefaultBuildableComponentIdResolveResult();
            if (dependencyFailure != null) {
                idResolveResult.failed(dependencyFailure);
            } else {
                ComponentOverrideMetadata overrideMetadata = DefaultComponentOverrideMetadata.forDependency(changing, firstDependencyArtifact, clientModule);
                resolver.resolve(componentSelector, overrideMetadata, acceptor, rejector, idResolveResult);
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

    /**
     * Create a component selection reason specific to this selector.
     * The reason produced here is not enhanced with 'unmatched' and 'rejected' descriptions.
     */
    public ComponentSelectionReasonInternal getSelectionReason() {
        return ComponentSelectionReasons.of(dependencyReasons);
    }

    /**
     * Append selection descriptors to the supplied "reason", enhancing with any 'unmatched' or 'rejected' reasons.
     */
    public void addReasonsForSelector(ComponentSelectionReasonInternal selectionReason) {
        ComponentIdResolveResult result = preferResult == null ? requireResult : preferResult;
        Collection<String> rejectedBySelector = null;
        if (result != null) {
            for (RejectedVersion rejectedVersion : result.getRejectedVersions()) {
                String version = rejectedVersion.getId().getVersion();
                if (rejectedVersion instanceof RejectedBySelectorVersion) {
                    if (rejectedBySelector == null) {
                        rejectedBySelector = new ArrayList<>();
                    }
                    rejectedBySelector.add(version);
                } else if (rejectedVersion instanceof RejectedByRuleVersion) {
                    String reason = ((RejectedByRuleVersion) rejectedVersion).getReason();
                    selectionReason.addCause(ComponentSelectionReasons.REJECTION.withDescription(new RejectedByRuleReason(version, reason)));
                } else if (rejectedVersion instanceof RejectedByAttributesVersion) {
                    selectionReason.addCause(ComponentSelectionReasons.REJECTION.withDescription(new RejectedByAttributesReason((RejectedByAttributesVersion) rejectedVersion)));
                }
            }
        }

        for (ComponentSelectionDescriptorInternal descriptor : dependencyReasons) {
            if (descriptor.getCause() == ComponentSelectionCause.REQUESTED || descriptor.getCause() == ComponentSelectionCause.CONSTRAINT) {
                if (rejectedBySelector != null) {
                    descriptor = descriptor.withDescription(new RejectedBySelectorReason(rejectedBySelector, descriptor));
                } else if (result != null && !result.getUnmatchedVersions().isEmpty()) {
                    descriptor = descriptor.withDescription(new UnmatchedVersionsReason(result.getUnmatchedVersions(), descriptor));
                }
            }
            selectionReason.addCause(descriptor);
        }
    }

    public boolean isConstraint() {
        return constraint;
    }

    @Override
    public IvyArtifactName getFirstDependencyArtifact() {
        return firstDependencyArtifact;
    }

    @Override
    @Deprecated
    public org.gradle.api.artifacts.ClientModule getClientModule() {
        return clientModule;
    }

    @Override
    public boolean isChanging() {
        return changing;
    }

    @Override
    public ResolvedVersionConstraint getVersionConstraint() {
        return versionConstraint;
    }

    @Override
    public ComponentSelector getComponentSelector() {
        return componentSelector;
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

    @SuppressWarnings("deprecation")
    public void update(DependencyState dependencyState) {
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

        // TODO: What if the constraint is removed? We want to set this false then.
        // Would be nice if we didn't need to track this on the selector.
        if (!constraint && dependencyState.getDependency().isConstraint()) {
            constraint = true;
            resolved = false; // We treat the attributes of an edge differently if it is a constraint
        }

        if (dependencyState.getSubstitutionFailure() != null && dependencyFailure == null) {
            dependencyFailure = dependencyState.getSubstitutionFailure();
        }
        dependencyState.addSelectionReasons(dependencyReasons);

        org.gradle.api.artifacts.ClientModule nextClientModule = DefaultComponentOverrideMetadata.extractClientModule(dependencyState.getDependency());
        if (nextClientModule != null && !nextClientModule.equals(clientModule)) {
            if (clientModule == null) {
                clientModule = nextClientModule;
            } else {
                throw new InvalidUserDataException(dependencyState.getDependency().getSelector().getDisplayName() + " has more than one client module definitions.");
            }
        }

        changing = changing || dependencyState.getDependency().isChanging();

        if (firstDependencyArtifact == null) {
            List<IvyArtifactName> artifacts = dependencyState.getDependency().getArtifacts();
            firstDependencyArtifact = artifacts.isEmpty() ? null : artifacts.get(0);
        }
    }

    private static class UnmatchedVersionsReason implements Describable {
        private final Collection<String> rejectedVersions;
        private final ComponentSelectionDescriptorInternal descriptor;

        private UnmatchedVersionsReason(Collection<String> rejectedVersions, ComponentSelectionDescriptorInternal descriptor) {
            this.rejectedVersions = rejectedVersions;
            this.descriptor = descriptor;
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

        private final Collection<String> rejectedVersions;
        private final ComponentSelectionDescriptorInternal descriptor;

        private RejectedBySelectorReason(Collection<String> rejectedVersions, ComponentSelectionDescriptorInternal descriptor) {
            this.rejectedVersions = rejectedVersions;
            this.descriptor = descriptor;
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
    }

}
