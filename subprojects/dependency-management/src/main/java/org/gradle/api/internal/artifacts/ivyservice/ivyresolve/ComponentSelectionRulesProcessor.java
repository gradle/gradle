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

import com.google.common.collect.Lists;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.ComponentMetadata;
import org.gradle.api.artifacts.ComponentSelection;
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.internal.artifacts.ComponentSelectionInternal;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyModuleDescriptor;
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataDetailsAdapter;
import org.gradle.internal.Factory;
import org.gradle.internal.component.external.model.IvyModuleResolveMetaData;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData;
import org.gradle.internal.rules.SpecRuleAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

public class ComponentSelectionRulesProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentSelectionRulesProcessor.class);
    private static final String USER_CODE_ERROR = "Could not apply component selection rule with all().";

    public void apply(ComponentSelectionInternal selection, Collection<SpecRuleAction<? super ComponentSelection>> specRuleActions, Factory<? extends MutableModuleComponentResolveMetaData> metaDataSupplier) {
        MetadataProvider metadataProvider = new MetadataProvider(metaDataSupplier);

        List<SpecRuleAction<? super ComponentSelection>> noInputRules = Lists.newArrayList();
        List<SpecRuleAction<? super ComponentSelection>> inputRules = Lists.newArrayList();
        for (SpecRuleAction<? super ComponentSelection> specRuleAction : specRuleActions) {
            if (specRuleAction.getAction().getInputTypes().isEmpty()) {
                noInputRules.add(specRuleAction);
            } else {
                inputRules.add(specRuleAction);
            }
        }

        if (processRules(noInputRules, selection, metadataProvider)) {
            processRules(inputRules, selection, metadataProvider);
        }
    }

    private boolean processRules(List<SpecRuleAction<? super ComponentSelection>> specRuleActions, ComponentSelectionInternal selection, MetadataProvider metadataProvider) {
        for (SpecRuleAction<? super ComponentSelection> rule : specRuleActions) {
            processRule(selection, metadataProvider, rule);

            if (selection.isRejected()) {
                LOGGER.info(String.format("Selection of '%s' rejected by component selection rule: %s", selection.getCandidate(), ((ComponentSelectionInternal) selection).getRejectionReason()));
                return false;
            }
        }
        return true;
    }

    private void processRule(ComponentSelection selection, MetadataProvider metadataProvider, SpecRuleAction<? super ComponentSelection> specRuleAction) {
        if (!specRuleAction.getSpec().isSatisfiedBy(selection)) {
            return;
        }

        List<Object> inputs = Lists.newArrayList();
        for (Class<?> inputType : specRuleAction.getAction().getInputTypes()) {
            if (inputType == ModuleComponentResolveMetaData.class) {
                inputs.add(metadataProvider.getMetaData());
                continue;
            }
            if (inputType == ComponentMetadata.class) {
                inputs.add(metadataProvider.getComponentMetadata());
                continue;
            }
            if (inputType == IvyModuleDescriptor.class) {
                IvyModuleDescriptor ivyModuleDescriptor = metadataProvider.getIvyModuleDescriptor();
                if (ivyModuleDescriptor == null) {
                    // Rules that require ivy module descriptor input are not fired for non-ivy modules
                    return;
                }
                inputs.add(ivyModuleDescriptor);
                continue;
            }
            // We've already validated the inputs: should never get here.
            throw new IllegalStateException();
        }

        try {
            specRuleAction.getAction().execute(selection, inputs);
        } catch (Exception e) {
            throw new InvalidUserCodeException(USER_CODE_ERROR, e);
        }
    }

    private static class MetadataProvider {
        private final Factory<? extends MutableModuleComponentResolveMetaData> metaDataSupplier;
        private MutableModuleComponentResolveMetaData cachedMetaData;

        private MetadataProvider(Factory<? extends MutableModuleComponentResolveMetaData> metaDataSupplier) {
            this.metaDataSupplier = metaDataSupplier;
        }

        public ComponentMetadata getComponentMetadata() {
            return new ComponentMetadataDetailsAdapter(getMetaData());
        }

        public IvyModuleDescriptor getIvyModuleDescriptor() {
            ModuleComponentResolveMetaData metaData = getMetaData();
            if (metaData instanceof IvyModuleResolveMetaData) {
                IvyModuleResolveMetaData ivyMetadata = (IvyModuleResolveMetaData) metaData;
                return new DefaultIvyModuleDescriptor(ivyMetadata.getExtraInfo(), ivyMetadata.getBranch(), ivyMetadata.getStatus());
            }
            return null;
        }

        public MutableModuleComponentResolveMetaData getMetaData() {
            if (cachedMetaData == null) {
                cachedMetaData = metaDataSupplier.create();
            }
            return cachedMetaData;
        }
    }
}
