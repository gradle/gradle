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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.artifacts.CapabilityResolutionDetails;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser;
import org.gradle.api.provider.Provider;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Default implementation of {@link CapabilitiesResolutionInternal}.
 */
public class DefaultCapabilitiesResolution implements CapabilitiesResolutionInternal {

    private final CapabilityNotationParser capabilityNotationParser;

    private @Nullable List<RegisteredAction> actions;

    public DefaultCapabilitiesResolution(CapabilityNotationParser capabilityNotationParser) {
        this.capabilityNotationParser = capabilityNotationParser;
    }

    @Override
    public void all(Action<? super CapabilityResolutionDetails> action) {
        doAddAction(null, action);
    }

    @Override
    public void withCapability(Capability capability, Action<? super CapabilityResolutionDetails> action) {
        doAddAction(() -> capability, action);
    }

    @Override
    public void withCapability(String group, String name, Action<? super CapabilityResolutionDetails> action) {
        doAddAction(() -> new DefaultImmutableCapability(group, name, null), action);
    }

    @Override
    public void withCapability(Object notation, Action<? super CapabilityResolutionDetails> action) {
        Supplier<Capability> capabilitySupplier = notation instanceof Provider
            ? () -> ((Provider<?>) notation).map(capabilityNotationParser::parseNotation).get()
            : () -> capabilityNotationParser.parseNotation(notation);
        doAddAction(capabilitySupplier, action);
    }

    void doAddAction(
        @Nullable Supplier<Capability> notation,
        Action<? super CapabilityResolutionDetails> action
    ) {
        if (actions == null) {
            actions = new ArrayList<>();
        }
        actions.add(new RegisteredAction(notation, action));
    }

    @Override
    public ImmutableList<CapabilityResolutionRule> getRules() {
        if (actions == null || actions.isEmpty()) {
            return ImmutableList.of();
        }

        ImmutableList.Builder<CapabilityResolutionRule> builder = ImmutableList.builderWithExpectedSize(actions.size());
        for (RegisteredAction registeredAction : actions) {
            builder.add(registeredAction.asCapabilityResolutionAction());
        }
        return builder.build();
    }

    /**
     * Holds a supplier to the capability notation, allowing us to defer
     * parsing it until we actually need it.
     */
    private static class RegisteredAction {

        private final @Nullable Supplier<Capability> notation;
        private final Action<? super CapabilityResolutionDetails> action;

        RegisteredAction(
            @Nullable Supplier<Capability> notation,
            Action<? super CapabilityResolutionDetails> action
        ) {
            this.notation = notation;
            this.action = action;
        }

        public CapabilityResolutionRule asCapabilityResolutionAction() {
            if (notation == null) {
                return new CapabilityResolutionRule(null, action);
            }
            return new CapabilityResolutionRule(DefaultImmutableCapability.of(notation.get()), action);
        }

    }

}
