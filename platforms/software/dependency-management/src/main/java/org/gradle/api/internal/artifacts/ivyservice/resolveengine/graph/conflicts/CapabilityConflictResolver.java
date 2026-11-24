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
import org.gradle.api.Describable;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.CapabilityResolutionDetails;
import org.gradle.api.artifacts.ComponentVariantIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.NodeState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.api.internal.capabilities.ImmutableCapability;
import org.gradle.api.internal.notations.ComponentIdentifierParserFactory;
import org.gradle.internal.component.external.model.DefaultComponentVariantIdentifier;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.typeconversion.NotationParser;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Responsible for resolving capability conflicts.
 */
public class CapabilityConflictResolver {

    private final ImmutableList<CapabilitiesResolutionInternal.CapabilityResolutionRule> rules;
    private final Comparator<@Nullable Version> versionComparator;
    private final VersionParser versionParser;
    private final NotationParser<Object, ComponentIdentifier> componentNotationParser;

    public CapabilityConflictResolver(
        ImmutableList<CapabilitiesResolutionInternal.CapabilityResolutionRule> rules,
        Comparator<Version> versionComparator,
        VersionParser versionParser
    ) {
        this.rules = rules;
        // A capability without a version is lower than any capability with a version
        this.versionComparator = Comparator.nullsFirst(versionComparator);
        this.versionParser = versionParser;
        this.componentNotationParser = new ComponentIdentifierParserFactory().create();
    }

    /**
     * A node in conflict and the capability it provides that is in conflict.
     */
    private static class Candidate {

        final NodeState node;
        final ImmutableCapability capability;

        public Candidate(NodeState node, ImmutableCapability capability) {
            this.node = node;
            this.capability = capability;
        }

    }

    /**
     * Applies user-supplied capability conflict resolution rules to the set of candidate nodes.
     * Conflict resolution finishes with a single selected candidate, or all candidates being rejected.
     *
     * @param group The group of the capability in conflict.
     * @param name The name of the capability in conflict.
     * @param nodes The nodes that provide the capability in conflict.
     */
    public void resolve(String group, String name, Collection<NodeState> nodes) {
        // Candidates that are no longer selected are filtered out before this resolver is executed.
        // If there is only one candidate at the beginning of conflict resolution, select that candidate.
        if (nodes.size() == 1) {
            NodeState onlyNode = nodes.iterator().next();
            onlyNode.resolveCapabilityConflict(onlyNode);
            return;
        }

        ImmutableList<Candidate> candidates = discoverCandidates(group, name, nodes);
        SelectedCandidate winner = findSelectedCandidate(group, name, candidates);
        if (winner != null) {
            winner.node.resolveCapabilityConflict(winner.node);
            winner.node.getComponent().addCause(ComponentSelectionReasons.CONFLICT_RESOLUTION.withDescription(winner.reason));

            for (Candidate losingCandidate : candidates) {
                if (losingCandidate.node != winner.node) {
                    losingCandidate.node.resolveCapabilityConflict(winner.node);
                }
            }
        } else {
            // If no winner was selected, reject all candidates.
            for (Candidate candidate : candidates) {
                Set<NodeState> conflictedNodes = candidates.stream().map(c -> c.node)
                    .filter(node -> node != candidate.node)
                    .collect(Collectors.toSet());

                candidate.node.rejectForCapabilityConflict(candidate.capability, conflictedNodes);
            }
        }
    }

    private static ImmutableList<Candidate> discoverCandidates(String group, String name, Collection<NodeState> nodes) {
        ImmutableList.Builder<Candidate> candidates = ImmutableList.builderWithExpectedSize(nodes.size());
        for (NodeState node : nodes) {
            ImmutableCapability capability = node.findCapability(group, name);
            if (capability == null) {
                throw new IllegalArgumentException("Node " + node.getDisplayName() + " does not provide capability " + group + ":" + name);
            }
            candidates.add(new Candidate(node, capability));
        }
        return candidates.build();
    }

    /**
     * Contains the user-selected node, if any, and the reason for selecting it.
     */
    static class SelectedCandidate {

        final NodeState node;
        final Describable reason;

        SelectedCandidate(NodeState node, Describable reason) {
            this.node = node;
            this.reason = reason;
        }

    }

    /**
     * Successively applies all applicable capability resolution rules until a candidate is selected.
     * Returns null if no rules selected any candidate.
     */
    private @Nullable SelectedCandidate findSelectedCandidate(
        String group,
        String name,
        ImmutableList<Candidate> initialCandidates
    ) {
        ImmutableList<Candidate> candidates = initialCandidates;
        for (CapabilitiesResolutionInternal.CapabilityResolutionRule rule : rules) {
            if (rule.appliesTo(group, name)) {
                DefaultCapabilityResolutionDetails details = new DefaultCapabilityResolutionDetails(
                    componentNotationParser,
                    group,
                    name,
                    candidates
                );

                try {
                    rule.getAction().execute(details);
                } catch (Exception ex) {
                    if (ex instanceof InvalidUserCodeException) {
                        throw ex;
                    }
                    throw new InvalidUserCodeException("Capability resolution rule failed with an error", ex);
                }

                if (details.useHighest) {
                    ImmutableList<Candidate> highestVersions = findHighestVersions(candidates);
                    if (highestVersions.size() == 1) {
                        Describable selectionReason = details.reason != null
                            ? () -> userProvidedReason(group, name, details.reason)
                            : () -> "latest version of capability " + group + ":" + name;
                        return new SelectedCandidate(highestVersions.iterator().next().node, selectionReason);
                    } else {
                        candidates = highestVersions;
                    }
                } else if (details.selected != null) {
                    NodeState selectedNode = details.selected.node;
                    Describable selectionReason = details.reason != null
                        ? () -> userProvidedReason(group, name, details.reason)
                        : () -> "Explicit selection of " + selectedNode.getComponent().getComponentId().getDisplayName() + " variant " + selectedNode.getMetadata().getName();
                    return new SelectedCandidate(selectedNode, selectionReason);
                }
            }
        }

        return null;
    }

    private static String userProvidedReason(String group, String name, String reason) {
        return "On capability " + group + ":" + name + " " + reason;
    }

    /**
     * Find all candidates that have the highest version of the capability in conflict.
     * If all candidates have the same version, returns all candidates.
     * <p>
     * Versions are ordered the same way as module versions in conflict resolution.
     */
    private ImmutableList<Candidate> findHighestVersions(ImmutableList<Candidate> candidates) {
        Candidate first = candidates.get(0);
        Version highestVersion = parseVersion(first.capability.getVersion());
        List<Candidate> highestVersionCandidates = new ArrayList<>(candidates.size());
        highestVersionCandidates.add(first);
        for (Candidate candidate : candidates.subList(1, candidates.size())) {
            Version version = parseVersion(candidate.capability.getVersion());
            int comparison = versionComparator.compare(version, highestVersion);
            if (comparison > 0) {
                highestVersion = version;
                highestVersionCandidates.clear();
                highestVersionCandidates.add(candidate);
            } else if (comparison == 0) {
                highestVersionCandidates.add(candidate);
            }
        }

        return ImmutableList.copyOf(highestVersionCandidates);
    }

    private @Nullable Version parseVersion(@Nullable String version) {
        return version == null ? null : versionParser.transform(version);
    }

    private static class DefaultCapabilityResolutionDetails implements CapabilityResolutionDetails {

        private final NotationParser<Object, ComponentIdentifier> componentIdParser;
        private final String group;
        private final String name;
        private final ImmutableList<Candidate> candidates;

        // Mutable State
        private boolean useHighest;
        private @Nullable String reason;
        private @Nullable Candidate selected;

        private DefaultCapabilityResolutionDetails(
            NotationParser<Object, ComponentIdentifier> componentIdParser,
            String group,
            String name,
            ImmutableList<Candidate> candidates
        ) {
            this.componentIdParser = componentIdParser;
            this.group = group;
            this.name = name;
            this.candidates = candidates;
        }

        @Override
        public ImmutableCapability getCapability() {
            return new DefaultImmutableCapability(group, name, null);
        }

        @Override
        public ImmutableList<ComponentVariantIdentifier> getCandidates() {
            ImmutableList.Builder<ComponentVariantIdentifier> candidateIds = ImmutableList.builderWithExpectedSize(candidates.size());
            for (Candidate candidate : candidates) {
                candidateIds.add(new DefaultComponentVariantIdentifier(
                    candidate.node.getComponent().getComponentId(),
                    candidate.node.getMetadata().getName()
                ));
            }
            return candidateIds.build();
        }

        @Override
        public CapabilityResolutionDetails select(ComponentVariantIdentifier candidateId) {
            for (Candidate candidate : candidates) {
                if (candidate.node.getComponent().getComponentId().equals(candidateId.getId())) {
                    if (candidate.node.getMetadata().getName().equals(candidateId.getVariantName())) {
                        this.selected = candidate;
                        break;
                    }
                }
            }

            return this;
        }

        @Override
        public CapabilityResolutionDetails select(Object notation) {
            // TODO: This method only allows users to select a component identifier.
            //       However, it is the nodes of a component which participate in capability conflicts.
            //       This method arbitrarily selects the first candidate node from any given component,
            //       making it imprecise. We should fix this somehow or deprecate this method in favor
            //       of the other `select` method, which permits selecting a specific variant.
            ComponentIdentifier selectedComponentId = componentIdParser.parseNotation(notation);

            for (Candidate candidate : candidates) {
                ComponentIdentifier candidateComponentId = candidate.node.getComponent().getComponentId();

                if (selectedComponentId.equals(candidateComponentId)) {
                    this.selected = candidate;
                    return this;
                }

                if (candidateComponentId instanceof ModuleComponentIdentifier && selectedComponentId instanceof ModuleComponentIdentifier) {
                    // Since we are performing capability conflict resolution, there is only one candidate component per module.
                    // So, we can be lenient wrt the version number in the component ID.
                    ModuleComponentIdentifier candidateId = (ModuleComponentIdentifier) candidateComponentId;
                    ModuleComponentIdentifier selectedId = (ModuleComponentIdentifier) selectedComponentId;
                    if (candidateId.getModuleIdentifier().equals(selectedId.getModuleIdentifier())) {
                        this.selected = candidate;
                        return this;
                    }
                }
            }

            List<String> formattedCandidates = candidates.stream().map(c -> c.node.getDisplayName()).sorted().collect(Collectors.toList());
            throw new InvalidUserCodeException(selectedComponentId + " is not a valid candidate for conflict resolution on capability '" + group + ":" + name + "': candidates are " + formattedCandidates);
        }

        @Override
        public CapabilityResolutionDetails selectHighestVersion() {
            this.useHighest = true;
            return this;
        }

        @Override
        public CapabilityResolutionDetails because(String reason) {
            this.reason = reason;
            return this;
        }

    }

}
