/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.ComponentMetadata;
import org.gradle.api.artifacts.ComponentSelection;
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.internal.artifacts.ComponentSelectionInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.rules.SpecRuleAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ComponentSelectionRulesProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentSelectionRulesProcessor.class);

    private final Spec<SpecRuleAction<? super ComponentSelection>> withNoInputs = element -> element.getAction().getInputTypes().isEmpty();
    private final Spec<SpecRuleAction<? super ComponentSelection>> withInputs = Specs.negate(withNoInputs);

    void apply(ComponentSelectionInternal selection, Collection<SpecRuleAction<? super ComponentSelection>> specRuleActions, MetadataProvider metadataProvider) {
        if (processRules(specRuleActions, withNoInputs, selection, metadataProvider)) {
            processRules(specRuleActions, withInputs, selection, metadataProvider);
        }
    }

    private boolean processRules(Collection<SpecRuleAction<? super ComponentSelection>> specRuleActions, Spec<SpecRuleAction<? super ComponentSelection>> filter, ComponentSelectionInternal selection, MetadataProvider metadataProvider) {
        for (SpecRuleAction<? super ComponentSelection> rule : specRuleActions) {
            if (filter.isSatisfiedBy(rule)) {
                processRule(rule, selection, metadataProvider);

                if (selection.isRejected()) {
                    LOGGER.info("Selection of {} rejected by component selection rule: {}", selection.getCandidate().getDisplayName(), selection.getRejectionReason());
                    return false;
                }
            }
        }
        return true;
    }

    private void processRule(SpecRuleAction<? super ComponentSelection> rule, ComponentSelection selection, MetadataProvider metadataProvider) {
        if (!rule.getSpec().isSatisfiedBy(selection)) {
            return;
        }

        List<Object> inputValues = getInputValues(rule.getAction().getInputTypes(), metadataProvider);

        if (inputValues == null) {
            // Broken meta-data, bail
            return;
        }

        if (inputValues.contains(null)) {
            // If any of the input values are not available for this selection, ignore the rule
            return;
        }

        try {
            rule.getAction().execute(selection, inputValues);
        } catch (Exception e) {
            throw new InvalidUserCodeException(String.format("There was an error while evaluating a component selection rule for %s.", selection.getCandidate().getDisplayName()), e);
        }
    }

    private List<Object> getInputValues(List<Class<?>> inputTypes, MetadataProvider metadataProvider) {
        if (inputTypes.size() == 0) {
            return Collections.emptyList();
        }

        if (!metadataProvider.isUsable()) {
            return null;
        }

        List<Object> inputs = new ArrayList<>(inputTypes.size());
        for (Class<?> inputType : inputTypes) {
            if (inputType == ComponentMetadata.class) {
                inputs.add(metadataProvider.getComponentMetadata());
                continue;
            }
            if (inputType == IvyModuleDescriptor.class) {
                inputs.add(metadataProvider.getIvyModuleDescriptor());
                continue;
            }
            // We've already validated the inputs: should never get here.
            throw new IllegalStateException();
        }
        return inputs;
    }
}
