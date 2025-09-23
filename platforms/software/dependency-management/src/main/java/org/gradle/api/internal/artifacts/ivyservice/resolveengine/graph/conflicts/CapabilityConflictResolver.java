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
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.CapabilityResolutionDetails;
import org.gradle.api.artifacts.ComponentVariantIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal;
import org.gradle.api.internal.capabilities.CapabilityInternal;
import org.gradle.api.internal.capabilities.ImmutableCapability;
import org.gradle.api.internal.notations.ComponentIdentifierParserFactory;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.DefaultComponentVariantIdentifier;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.internal.VersionNumber;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CapabilityConflictResolver implements CapabilitiesConflictHandler.Resolver {

    private final NotationParser<Object, ComponentIdentifier> componentNotationParser = new ComponentIdentifierParserFactory().create();
    private final ImmutableList<CapabilitiesResolutionInternal.CapabilityResolutionRule> rules;

    public CapabilityConflictResolver(ImmutableList<CapabilitiesResolutionInternal.CapabilityResolutionRule> rules) {
        this.rules = rules;
    }

    @Override
    public void resolve(CapabilitiesConflictHandler.ResolutionDetails details) {
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

    private void handleCapabilityAction(CapabilitiesConflictHandler.ResolutionDetails details, Capability key, List<? extends Capability> versions, DefaultCapabilityResolutionDetails resolutionDetails) {
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

    private void performCapabilitySelection(CapabilitiesConflictHandler.ResolutionDetails details, List<? extends Capability> versions, DefaultCapabilityResolutionDetails resolutionDetails) {
        if (resolutionDetails.useHighest) {
            selectHightestVersion(details);
        } else if (resolutionDetails.selected != null) {
            versions.forEach(version -> details.getCandidates(version).forEach(cand -> selectExplicitCandidate(resolutionDetails, (CapabilityInternal) version, cand)));
        }
    }

    private void selectHightestVersion(CapabilitiesConflictHandler.ResolutionDetails details) {
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

    private void selectExplicitCandidate(DefaultCapabilityResolutionDetails resolutionDetails, CapabilityInternal version, CapabilitiesConflictHandler.CandidateDetails cand) {
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

}
