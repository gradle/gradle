/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.gradle.api.Describable;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.CapabilityResolutionDetails;
import org.gradle.api.artifacts.ComponentVariantIdentifier;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.NodeState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ResolveState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.api.internal.capabilities.CapabilityInternal;
import org.gradle.api.internal.capabilities.ImmutableCapability;
import org.gradle.api.internal.notations.ComponentIdentifierParserFactory;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.DefaultComponentVariantIdentifier;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.internal.VersionNumber;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CapabilityConflictResolver {


    private final ResolveState resolveState;
    private final ImmutableList<CapabilitiesResolutionInternal.CapabilityResolutionRule> rules;
    private final NotationParser<Object, ComponentIdentifier> componentNotationParser;

    public CapabilityConflictResolver(
        ResolveState resolveState,
        ImmutableList<CapabilitiesResolutionInternal.CapabilityResolutionRule> rules
    ) {
        this.resolveState = resolveState;
        this.rules = rules;
        this.componentNotationParser = new ComponentIdentifierParserFactory().create();
    }

    /**
     * Perform conflict resolution, selecting one of the candidates or rejecting all of them if none can be selected.
     */
    public void resolve(CapabilitiesConflictHandler.CapabilityConflict conflict) {
        Details details = new Details(conflict);

        // Candidates that are no longer selected are filtered out before these resolvers are executed.
        // If there is only one candidate at the beginning of conflict resolution, select that candidate.
        resolveLastCandidate(details);
        if (details.hasResult()) {
            applyConflictResolution(details, conflict);
            return;
        }

        // Otherwise, let the user resolvers reject candidates.
        applyUserDefinedRules(details);
        if (details.hasResult()) {
            applyConflictResolution(details, conflict);
            return;
        }

        // If there is one candidate left after the user resolvers are executed, select that candidate.
        resolveLastCandidate(details);
        if (details.hasResult()) {
            applyConflictResolution(details, conflict);
            return;
        }

        // Otherwise, reject all remaining candidates.
        Collection<? extends Capability> capabilityVersions = details.getCapabilityVersions();
        for (Capability capabilityVersion : capabilityVersions) {
            Collection<? extends CandidateDetails> candidates = details.getCandidates(capabilityVersion);
            if (!candidates.isEmpty()) {
                // Arbitrarily select and mark all as rejected
                for (CandidateDetails candidate : candidates) {
                    candidate.reject();
                }
            }
        }
    }

    private void applyConflictResolution(Details details, CapabilitiesConflictHandler.CapabilityConflict conflict) {
        // Visit the winning module first so that when we visit unattached dependencies of
        // losing modules, the winning module always has a selected component.
        Set<ModuleIdentifier> seen = new HashSet<>();
        ModuleIdentifier winningModule = details.getSelected().getModule().getId();
        resolveState.getModule(winningModule).replaceWith(details.getSelected());
        seen.add(winningModule);

        for (NodeState node : details.conflict.getNodes()) {
            ModuleIdentifier module = node.getComponent().getModule().getId();
            if (seen.add(module)) {
                resolveState.getModule(module).replaceWith(details.getSelected());
            }
        }

        if (conflict.getNodes().size() > 1) {
            assert details.reason != null;
            details.getSelected().addCause(ComponentSelectionReasons.CONFLICT_RESOLUTION.withDescription(details.reason));
        }
    }

    private static void resolveLastCandidate(Details details) {
        Collection<? extends Capability> capabilityVersions = details.getCapabilityVersions();
        CandidateDetails single = null;
        for (Capability capabilityVersion : capabilityVersions) {
            Collection<? extends CandidateDetails> candidates = details.getCandidates(capabilityVersion);
            int size = candidates.size();
            if (size >= 1) {
                if (size == 1 && single == null) {
                    single = candidates.iterator().next();
                } else {
                    // not a single candidate
                    return;
                }
            }
        }
        if (single != null) {
            single.select();
        }
    }

    private static class Details implements ResolutionDetails {
        private final CapabilitiesConflictHandler.CapabilityConflict conflict;
        private final Set<NodeState> evicted = new HashSet<>();
        private NodeState selected;
        private Describable reason;

        private Details(CapabilitiesConflictHandler.CapabilityConflict conflict) {
            this.conflict = conflict;
        }

        @Override
        public Collection<? extends Capability> getCapabilityVersions() {
            return conflict.getDescriptors();
        }

        @Override
        public Collection<? extends CandidateDetails> getCandidates(Capability capability) {
            ImmutableList.Builder<CandidateDetails> candidates = new ImmutableList.Builder<>();
            String group = capability.getGroup();
            String name = capability.getName();
            String version = capability.getVersion();
            for (final NodeState node : conflict.getNodes()) {
                if (!evicted.contains(node)) {
                    Capability componentCapability = node.findCapability(group, name);
                    if (componentCapability != null && componentCapability.getVersion().equals(version)) {
                        candidates.add(new CandidateDetails() {
                            @Override
                            public ComponentIdentifier getId() {
                                return node.getComponent().getComponentId();
                            }

                            @Override
                            public String getVariantName() {
                                return node.getMetadata().getName();
                            }

                            @Override
                            public void evict() {
                                node.evict();
                                evicted.add(node);
                            }

                            @Override
                            public void select() {
                                selected = node;
                            }

                            @Override
                            public void reject() {
                                ComponentState component = node.getComponent();
                                component.rejectForCapabilityConflict(capability, conflictedNodes(node, conflict.getNodes()));
                                component.getModule().replaceWith(component);
                            }

                            @Override
                            public void byReason(Describable description) {
                                reason = description;
                            }
                        });
                    }
                }
            }
            return candidates.build();
        }

        private Collection<NodeState> conflictedNodes(NodeState node, Collection<NodeState> nodes) {
            List<NodeState> conflictedNodes = new ArrayList<>(nodes);
            conflictedNodes.remove(node);
            return conflictedNodes;
        }

        @Override
        public boolean hasResult() {
            return selected != null;
        }

        @Override
        public ComponentState getSelected() {
            return selected.getComponent();
        }
    }

    private void applyUserDefinedRules(ResolutionDetails details) {
        details.getCapabilityVersions().stream()
            .collect(Collectors.groupingBy(c -> new DefaultImmutableCapability(c.getGroup(), c.getName(), null)))
            .forEach((capability, versions) -> {
                List<ComponentVariantIdentifier> candidateIds = versions.stream()
                    .flatMap(c -> details.getCandidates(c).stream())
                    .map(detail -> new DefaultComponentVariantIdentifier(detail.getId(), detail.getVariantName()))
                    .collect(Collectors.toList());
                DefaultCapabilityResolutionDetails resolutionDetails = new DefaultCapabilityResolutionDetails(componentNotationParser, capability, candidateIds);
                handleCapabilityAction(details, capability, versions, resolutionDetails);
            });
    }

    private void handleCapabilityAction(ResolutionDetails details, Capability key, List<? extends Capability> versions, DefaultCapabilityResolutionDetails resolutionDetails) {
        for (CapabilitiesResolutionInternal.CapabilityResolutionRule action : rules) {
            ImmutableCapability capability = action.getTargetCapability();
            if (capability == null || (key.getGroup().equals(capability.getGroup()) && key.getName().equals(capability.getName()))) {
                try {
                    action.getAction().execute(resolutionDetails);
                } catch (Exception ex) {
                    if (ex instanceof InvalidUserCodeException) {
                        throw ex;
                    }
                    throw new InvalidUserCodeException("Capability resolution rule failed with an error", ex);
                }
                if (resolutionDetails.didSomething) {
                    performCapabilitySelection(details, versions, resolutionDetails);
                }
            }
        }
    }

    private void performCapabilitySelection(ResolutionDetails details, List<? extends Capability> versions, DefaultCapabilityResolutionDetails resolutionDetails) {
        if (resolutionDetails.useHighest) {
            selectHightestVersion(details);
        } else if (resolutionDetails.selected != null) {
            versions.forEach(version -> details.getCandidates(version).forEach(cand -> selectExplicitCandidate(resolutionDetails, (CapabilityInternal) version, cand)));
        }
    }

    private void selectHightestVersion(ResolutionDetails details) {
        Collection<? extends Capability> capabilityVersions = details.getCapabilityVersions();
        if (capabilityVersions.size() > 1) {
            Set<Capability> sorted = Sets.newTreeSet((o1, o2) -> {
                VersionNumber v1 = VersionNumber.parse(o1.getVersion());
                VersionNumber v2 = VersionNumber.parse(o2.getVersion());
                return v2.compareTo(v1);
            });
            sorted.addAll(capabilityVersions);
            boolean first = true;
            for (Capability capability : sorted) {
                DisplayName reason = Describables.of("latest version of capability", ((CapabilityInternal) capability).getCapabilityId());
                boolean isFirst = first;
                details.getCandidates(capability).forEach(cand -> {
                    cand.byReason(reason);
                    if (!isFirst) {
                        cand.evict();
                    }
                });
                first = false;
            }
        }
    }

    private void selectExplicitCandidate(DefaultCapabilityResolutionDetails resolutionDetails, CapabilityInternal version, CandidateDetails cand) {
        if (cand.getId().equals(resolutionDetails.selected.getId())) {
            if (cand.getVariantName().equals(resolutionDetails.selected.getVariantName())) {
                cand.select();
                String reason = resolutionDetails.reason;
                if (reason != null) {
                    cand.byReason(Describables.of("On capability", version.getCapabilityId(), reason));
                } else {
                    cand.byReason(() -> String.format("Explicit selection of %s variant %s",
                        resolutionDetails.selected.getId().getDisplayName(),
                        resolutionDetails.selected.getVariantName()
                    ));
                }
            } else {
                cand.evict();
            }
        }
    }

    private static class DefaultCapabilityResolutionDetails implements CapabilityResolutionDetails {

        private final NotationParser<Object, ComponentIdentifier> notationParser;
        private final Capability capability;
        private final List<ComponentVariantIdentifier> candidates;

        boolean didSomething;
        boolean useHighest;
        private String reason;
        private ComponentVariantIdentifier selected;

        private DefaultCapabilityResolutionDetails(NotationParser<Object, ComponentIdentifier> notationParser, Capability capability, List<ComponentVariantIdentifier> candidates) {
            this.notationParser = notationParser;
            this.capability = capability;
            this.candidates = candidates;
        }

        @Override
        public Capability getCapability() {
            return capability;
        }

        @Override
        public List<ComponentVariantIdentifier> getCandidates() {
            return candidates;
        }

        @Override
        public CapabilityResolutionDetails select(ComponentVariantIdentifier candidate) {
            didSomething = true;
            selected = candidate;
            return this;
        }

        @Override
        public CapabilityResolutionDetails select(Object notation) {
            ComponentIdentifier componentIdentifier = notationParser.parseNotation(notation);
            for (ComponentVariantIdentifier candidate : candidates) {
                if (componentIdentifier.equals(candidate.getId())) {
                    select(candidate);
                    return this;
                }
                if (candidate.getId() instanceof ModuleComponentIdentifier && componentIdentifier instanceof ModuleComponentIdentifier) {
                    // because it's a capability conflict resolution, there is only one candidate per module identifier
                    // so we can be lenient wrt the version number used in the descriptor, which helps whenever the user
                    // used the convenience "notation" method
                    ModuleComponentIdentifier candMCI = (ModuleComponentIdentifier) candidate.getId();
                    ModuleComponentIdentifier compMCI = (ModuleComponentIdentifier) componentIdentifier;
                    if (candMCI.getModuleIdentifier().equals(compMCI.getModuleIdentifier())) {
                        select(candidate);
                        return this;
                    }
                }
            }
            throw new InvalidUserCodeException(componentIdentifier + " is not a valid candidate for conflict resolution on capability " + capability + ": candidates are " + candidates);
        }

        @Override
        public CapabilityResolutionDetails selectHighestVersion() {
            didSomething = true;
            useHighest = true;
            return this;
        }

        @Override
        public CapabilityResolutionDetails because(String reason) {
            this.reason = reason;
            return this;
        }

    }

    interface ResolutionDetails {

        /**
         * The actual selected component.
         */
        ComponentState getSelected();

        Collection<? extends Capability> getCapabilityVersions();
        Collection<? extends CandidateDetails> getCandidates(Capability capability);
        boolean hasResult();
    }

    interface CandidateDetails {
        ComponentIdentifier getId();
        String getVariantName();
        void evict();
        void select();
        void reject();
        void byReason(Describable description);
    }

}
