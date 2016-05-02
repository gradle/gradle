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

package org.gradle.api.internal.artifacts;

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRuleProvider;
import org.gradle.internal.Actions;
import org.gradle.util.CollectionUtils;

import java.util.List;

public class DefaultGlobalDependencyResolutionRules implements GlobalDependencyResolutionRules {
    private final ComponentMetadataProcessor componentMetadataProcessor;
    private final ComponentModuleMetadataProcessor moduleMetadataProcessor;
    private final Action<DependencySubstitution> globalDependencySubstitutionRule;

    public DefaultGlobalDependencyResolutionRules(ComponentMetadataProcessor componentMetadataProcessor,
                                                  ComponentModuleMetadataProcessor moduleMetadataProcessor,
                                                  List<DependencySubstitutionRuleProvider> ruleProviders) {
        this.componentMetadataProcessor = componentMetadataProcessor;
        this.moduleMetadataProcessor = moduleMetadataProcessor;
        List<Action<DependencySubstitution>> globalActions = CollectionUtils.collect(ruleProviders, new Transformer<Action<DependencySubstitution>, DependencySubstitutionRuleProvider>() {
            @Override
            public Action<DependencySubstitution> transform(DependencySubstitutionRuleProvider dependencySubstitutionRuleProvider) {
                return dependencySubstitutionRuleProvider.getDependencySubstitutionRule();
            }
        });
        this.globalDependencySubstitutionRule = Actions.composite(globalActions);
    }

    public ComponentMetadataProcessor getComponentMetadataProcessor() {
        return componentMetadataProcessor;
    }

    public ComponentModuleMetadataProcessor getModuleMetadataProcessor() {
        return moduleMetadataProcessor;
    }

    @Override
    public Action<DependencySubstitution> getDependencySubstitutionRule() {
        return globalDependencySubstitutionRule;
    }
}
