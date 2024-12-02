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

import org.gradle.api.artifacts.DependencyArtifactSelector;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.ArtifactSelectionDetailsInternal;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.internal.Describables;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ForcingDependencyMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.BY_ANCESTOR;
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.CONSTRAINT;
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.FORCED;
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.REQUESTED;

/**
 * A declared dependency, potentially transformed based on a substitution.
 */
class DependencyState {

    /**
     * The original requested component, before substitution.
     */
    private final ComponentSelector requested;

    /**
     * The declared dependency this state is based off of, after substitution.
     */
    private final DependencyMetadata dependency;

    private final List<ComponentSelectionDescriptorInternal> ruleDescriptors;
    private final ComponentSelectorConverter componentSelectorConverter;
    private final ModuleVersionResolveException substitutionFailure;
    private final int hashCode;

    private ModuleIdentifier moduleIdentifier;
    private boolean reasonsAlreadyAdded;
    private Map<DependencySubstitutionApplicator.SubstitutionResult, DependencyState> substitutionResultMap;

    DependencyState(DependencyMetadata dependency, ComponentSelectorConverter componentSelectorConverter) {
        this(dependency, dependency.getSelector(), Collections.emptyList(), componentSelectorConverter, null);
    }

    private DependencyState(
        DependencyMetadata dependency,
        ComponentSelector requested,
        List<ComponentSelectionDescriptorInternal> ruleDescriptors,
        ComponentSelectorConverter componentSelectorConverter,
        @Nullable ModuleVersionResolveException substitutionFailure
    ) {
        this.dependency = dependency;
        this.requested = requested;
        this.ruleDescriptors = ruleDescriptors;
        this.componentSelectorConverter = componentSelectorConverter;
        this.substitutionFailure = substitutionFailure;
        this.hashCode = computeHashCode();
    }

    private int computeHashCode() {
        int hashCode = dependency.hashCode();
        hashCode = 31 * hashCode + requested.hashCode();
        return hashCode;
    }

    public ComponentSelector getRequested() {
        return requested;
    }

    public DependencyMetadata getDependency() {
        return dependency;
    }

    @Nullable
    public ModuleVersionResolveException getSubstitutionFailure() {
        return substitutionFailure;
    }

    public ModuleIdentifier getModuleIdentifier() {
        if (moduleIdentifier == null) {
            moduleIdentifier = componentSelectorConverter.getModule(dependency.getSelector());
        }
        return moduleIdentifier;
    }

    private DependencyState withTarget(ComponentSelector target, List<ComponentSelectionDescriptorInternal> ruleDescriptors) {
        DependencyMetadata targeted = dependency.withTarget(target);
        return new DependencyState(targeted, requested, ruleDescriptors, componentSelectorConverter, substitutionFailure);
    }

    private DependencyState withTargetAndArtifacts(ComponentSelector target, List<DependencyArtifactSelector> targetSelectors, List<ComponentSelectionDescriptorInternal> ruleDescriptors) {
        DependencyMetadata targeted = dependency.withTargetAndArtifacts(target, toIvyArtifacts(target, targetSelectors));
        return new DependencyState(targeted, requested, ruleDescriptors, componentSelectorConverter, substitutionFailure);
    }

    private List<IvyArtifactName> toIvyArtifacts(ComponentSelector target, List<DependencyArtifactSelector> targetSelectors) {
        return targetSelectors.stream()
            .map(avs -> createArtifact(target, avs))
            .collect(Collectors.toList());
    }

    private DefaultIvyArtifactName createArtifact(ComponentSelector target, DependencyArtifactSelector avs) {
        String extension = avs.getExtension() != null ? avs.getExtension() : avs.getType();
        return new DefaultIvyArtifactName(
            nameOf(target),
            avs.getType(),
            extension,
            avs.getClassifier()
        );
    }

    private static String nameOf(ComponentSelector target) {
        if (target instanceof ModuleComponentSelector) {
            return ((ModuleComponentSelector) target).getModule();
        }
        throw new IllegalStateException("Substitution with artifacts for something else than a module is not supported");
    }


    public boolean isForced() {
        if (!ruleDescriptors.isEmpty()) {
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

    void addSelectionReasons(List<ComponentSelectionDescriptorInternal> reasons) {
        if (reasonsAlreadyAdded) {
            return;
        }
        reasonsAlreadyAdded = true;
        addMainReason(reasons);

        if (!ruleDescriptors.isEmpty()) {
            addRuleDescriptors(reasons);
        }
        if (isDependencyForced()) {
            maybeAddReason(reasons, FORCED);
        }
    }

    private void addRuleDescriptors(List<ComponentSelectionDescriptorInternal> reasons) {
        for (ComponentSelectionDescriptorInternal descriptor : ruleDescriptors) {
            maybeAddReason(reasons, descriptor);
        }
    }

    private void addMainReason(List<ComponentSelectionDescriptorInternal> reasons) {
        ComponentSelectionDescriptorInternal dependencyDescriptor;
        if (reasons.contains(BY_ANCESTOR)) {
            dependencyDescriptor = BY_ANCESTOR;
        } else {
            dependencyDescriptor = dependency.isConstraint() ? CONSTRAINT : REQUESTED;
        }
        String reason = dependency.getReason();
        if (reason != null) {
            dependencyDescriptor = dependencyDescriptor.withDescription(Describables.of(reason));
        }
        maybeAddReason(reasons, dependencyDescriptor);
    }

    private static void maybeAddReason(List<ComponentSelectionDescriptorInternal> reasons, ComponentSelectionDescriptorInternal reason) {
        if (reasons.isEmpty()) {
            reasons.add(reason);
        } else if (isNewReason(reasons, reason)) {
            reasons.add(reason);
        }
    }

    private static boolean isNewReason(List<ComponentSelectionDescriptorInternal> reasons, ComponentSelectionDescriptorInternal reason) {
        return (reasons.size() == 1 && !reason.equals(reasons.get(0)))
            || !reasons.contains(reason);
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
        // This is a performance optimization, dependency states are deduplicated
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public DependencyState withSubstitution(DependencySubstitutionApplicator.SubstitutionResult substitutionResult, DependencySubstitutionInternal details) {
        if (substitutionResultMap == null) {
            substitutionResultMap = new HashMap<>();
        }

        // This caching works because our substitutionResult are cached themselves
        return substitutionResultMap.computeIfAbsent(substitutionResult, result -> {
            ArtifactSelectionDetailsInternal artifactSelectionDetails = details.getArtifactSelectionDetails();
            if (artifactSelectionDetails.isUpdated()) {
                return withTargetAndArtifacts(details.getTarget(), artifactSelectionDetails.getTargetSelectors(), details.getRuleDescriptors());
            }
            return withTarget(details.getTarget(), details.getRuleDescriptors());
        });
    }

    public DependencyState withSubstitutionFailure(ModuleVersionResolveException failure) {
        return new DependencyState(dependency, requested, ruleDescriptors, componentSelectorConverter, failure);
    }
}
