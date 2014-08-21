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

package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy;

import com.google.common.collect.Lists;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidActionClosureException;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.RuleAction;
import org.gradle.api.artifacts.ComponentMetadata;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.VersionSelection;
import org.gradle.api.artifacts.VersionSelectionRules;
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.internal.NoInputsRuleAction;
import org.gradle.api.internal.ClosureBackedRuleAction;
import org.gradle.api.internal.artifacts.VersionSelectionInternal;
import org.gradle.api.internal.artifacts.VersionSelectionRulesInternal;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyModuleDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaDataResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DefaultBuildableModuleVersionMetaDataResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess;
import org.gradle.api.internal.artifacts.metadata.IvyModuleVersionMetaData;
import org.gradle.api.internal.artifacts.metadata.MutableModuleVersionMetaData;
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataDetailsAdapter;

import java.util.*;

public class DefaultVersionSelectionRules implements VersionSelectionRulesInternal {
    final Set<RuleAction<? super VersionSelection>> versionSelectionRules = new LinkedHashSet<RuleAction<? super VersionSelection>>();

    private final static List<Class<?>> VALID_INPUT_TYPES = Lists.newArrayList(ComponentMetadata.class, IvyModuleDescriptor.class);

    private final static String USER_CODE_ERROR = "Could not apply version selection rule with all().";
    private static final String UNSUPPORTED_PARAMETER_TYPE_ERROR = "Unsupported parameter type for version selection rule: %s";

    public void apply(VersionSelection selection, ModuleComponentRepositoryAccess moduleAccess) {
        for (RuleAction<? super VersionSelection> rule : versionSelectionRules) {
            List<Object> inputs = Lists.newArrayList();
            for (Class<?> inputType : rule.getInputTypes()) {
                if (inputType == ComponentMetadata.class) {
                    MutableModuleVersionMetaData metaData = getMetaData(selection, moduleAccess);
                    ComponentMetadataDetails componentMetadata = new ComponentMetadataDetailsAdapter(metaData);
                    inputs.add(componentMetadata);
                    continue;
                }
                if (inputType == IvyModuleDescriptor.class) {
                    MutableModuleVersionMetaData metaData = getMetaData(selection, moduleAccess);
                    if (metaData instanceof IvyModuleVersionMetaData) {
                        IvyModuleVersionMetaData ivyMetadata = (IvyModuleVersionMetaData) metaData;
                        inputs.add(new DefaultIvyModuleDescriptor(ivyMetadata.getExtraInfo(), ivyMetadata.getBranch(), ivyMetadata.getStatus()));
                        continue;
                    } else {
                        return;
                    }
                }
                throw new InvalidUserCodeException(String.format(UNSUPPORTED_PARAMETER_TYPE_ERROR, inputType.getName()));
            }

            try {
                rule.execute(selection, inputs);
            } catch (Exception e) {
                throw new InvalidUserCodeException(USER_CODE_ERROR, e);
            }
        }
    }

    private MutableModuleVersionMetaData getMetaData(VersionSelection selection, ModuleComponentRepositoryAccess moduleAccess) {
        BuildableModuleVersionMetaDataResolveResult descriptorResult = new DefaultBuildableModuleVersionMetaDataResolveResult();
        moduleAccess.resolveComponentMetaData(((VersionSelectionInternal) selection).getDependencyMetaData(), selection.getCandidate(), descriptorResult);
        return descriptorResult.getMetaData();
    }

    public boolean hasRules() {
        return versionSelectionRules.size() > 0;
    }

    public VersionSelectionRules all(Action<? super VersionSelection> selectionAction) {
        versionSelectionRules.add(new NoInputsRuleAction<VersionSelection>(selectionAction));
        return this;
    }

    public VersionSelectionRules all(RuleAction<? super VersionSelection> ruleAction) {
        versionSelectionRules.add(validateInputTypes(ruleAction));
        return this;
    }

    public VersionSelectionRules all(Closure<?> closure) {
        versionSelectionRules.add(createRuleActionFromClosure(closure));
        return this;
    }

    private RuleAction<? super VersionSelection> createRuleActionFromClosure(Closure<?> closure) {
        try {
            return validateInputTypes(new ClosureBackedRuleAction<VersionSelection>(VersionSelection.class, closure));
        } catch (RuntimeException e) {
            throw new InvalidActionClosureException(String.format("The closure provided is not valid as a rule action for '%s'.", VersionSelectionRules.class.getSimpleName()), closure, e);
        }
    }

    private RuleAction<? super VersionSelection> validateInputTypes(RuleAction<? super VersionSelection> ruleAction) {
        for (Class<?> inputType : ruleAction.getInputTypes()) {
            if (!VALID_INPUT_TYPES.contains(inputType)) {
                throw new InvalidUserCodeException(String.format(UNSUPPORTED_PARAMETER_TYPE_ERROR, inputType.getName()));
            }
        }
        return ruleAction;
    }
}
