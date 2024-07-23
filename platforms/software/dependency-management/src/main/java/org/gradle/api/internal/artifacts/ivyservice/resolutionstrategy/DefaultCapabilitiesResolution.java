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
package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.CapabilityResolutionDetails;
import org.gradle.api.artifacts.ComponentVariantIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.CapabilitiesConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.UpgradeCapabilityResolver;
import org.gradle.api.internal.capabilities.CapabilityInternal;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.DefaultComponentVariantIdentifier;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.typeconversion.NotationParser;

import java.util.List;
import java.util.stream.Collectors;

public class DefaultCapabilitiesResolution implements CapabilitiesResolutionInternal {
    private final UpgradeCapabilityResolver upgradeCapabilityResolver = new UpgradeCapabilityResolver();

    private final NotationParser<Object, Capability> capabilityNotationParser;
    private final NotationParser<Object, ComponentIdentifier> componentNotationParser;
    private final List<CapabilityAction> actions = Lists.newArrayListWithExpectedSize(2);

    public DefaultCapabilitiesResolution(NotationParser<Object, Capability> capabilityNotationParser,
                                         NotationParser<Object, ComponentIdentifier> componentNotationParser) {
        this.capabilityNotationParser = capabilityNotationParser;
        this.componentNotationParser = componentNotationParser;
    }

    @Override
    public void all(Action<? super CapabilityResolutionDetails> action) {
        actions.add(new CapabilityAction(Specs.SATISFIES_ALL, action));
    }

    @Override
    public void withCapability(Capability capability, Action<? super CapabilityResolutionDetails> action) {
        withCapability(Providers.of(capability), action);
    }

    @Override
    public void withCapability(String group, String name, Action<? super CapabilityResolutionDetails> action) {
        withCapability(capabilityNotationParser.parseNotation(group + ":" + name), action);
    }

    @Override
    public void withCapability(Object notation, Action<? super CapabilityResolutionDetails> action) {
        if (notation instanceof Provider) {
            withCapability(((Provider<?>) notation).map(capabilityNotationParser::parseNotation), action);
            return;
        }
        withCapability(capabilityNotationParser.parseNotation(notation), action);
    }

    private void withCapability(Provider<? extends Capability> capability, Action<? super CapabilityResolutionDetails> action) {
        actions.add(new CapabilityAction(new CapabilitySpec(capability), action));
    }

    @Override
    public void apply(CapabilitiesConflictHandler.ResolutionDetails details) {
        details.getCapabilityVersions().stream()
            .collect(Collectors.groupingBy(c -> new DefaultImmutableCapability(c.getGroup(), c.getName(), null)))
            .forEach((key1, versions) -> {
                List<ComponentVariantIdentifier> candidateIds = versions.stream()
                    .flatMap(c -> details.getCandidates(c).stream())
                    .map(detail -> new DefaultComponentVariantIdentifier(detail.getId(), detail.getVariantName()))
                    .collect(Collectors.toList());
                DefaultCapabilityResolutionDetails resolutionDetails = new DefaultCapabilityResolutionDetails(componentNotationParser, key1, candidateIds);
                handleCapabilityAction(details, key1, versions, resolutionDetails);
            });

    }

    private void handleCapabilityAction(CapabilitiesConflictHandler.ResolutionDetails details, Capability key, List<? extends Capability> versions, DefaultCapabilityResolutionDetails resolutionDetails) {
        for (CapabilityAction action : actions) {
            if (action.predicate.isSatisfiedBy(key)) {
                action.execute(resolutionDetails);
                if (resolutionDetails.didSomething) {
                    performCapabilitySelection(details, versions, resolutionDetails);
                }
            }
        }
    }

    private void performCapabilitySelection(CapabilitiesConflictHandler.ResolutionDetails details, List<? extends Capability> versions, DefaultCapabilityResolutionDetails resolutionDetails) {
        if (resolutionDetails.useHighest) {
            upgradeCapabilityResolver.resolve(details);
        } else if (resolutionDetails.selected != null) {
            versions.forEach(version -> details.getCandidates(version).forEach(cand -> selectExplicitCandidate(resolutionDetails, (CapabilityInternal) version, cand)));
        }
    }

    private void selectExplicitCandidate(DefaultCapabilityResolutionDetails resolutionDetails, CapabilityInternal version, CapabilitiesConflictHandler.CandidateDetails cand) {
        if (cand.getId().equals(resolutionDetails.selected.getId())) {
            if (cand.getVariantName().equals(resolutionDetails.selected.getVariantName())) {
                cand.select();
                String reason = resolutionDetails.reason;
                if (reason != null) {
                    cand.byReason(Describables.of("On capability", version.getCapabilityId(), reason));
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

    private static class CapabilityAction {
        private final Spec<? super Capability> predicate;
        private final Action<? super CapabilityResolutionDetails> action;

        private CapabilityAction(Spec<? super Capability> predicate, Action<? super CapabilityResolutionDetails> action) {
            this.predicate = predicate;
            this.action = action;
        }

        public void execute(DefaultCapabilityResolutionDetails resolutionDetails) {
            try {
                action.execute(resolutionDetails);
            } catch (Exception ex) {
                if (ex instanceof InvalidUserCodeException) {
                    throw ex;
                }
                throw new InvalidUserCodeException("Capability resolution rule failed with an error", ex);
            }
        }
    }

    private static class CapabilitySpec implements Spec<Capability> {
        private final Provider<? extends Capability> capability;

        public CapabilitySpec(Provider<? extends Capability> capability) {
            this.capability = capability;
        }

        @Override
        public boolean isSatisfiedBy(Capability element) {
            Capability cap = capability.get();
            return element.getGroup().equals(cap.getGroup()) && element.getName().equals(cap.getName());
        }
    }
}
