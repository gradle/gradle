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
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRules;
import org.gradle.internal.Actions;
import org.gradle.util.CollectionUtils;

import java.util.List;

public class DefaultGlobalDependencyResolutionRules implements GlobalDependencyResolutionRules {
    private final ComponentMetadataProcessorFactory componentMetadataProcessorFactory;
    private final ComponentModuleMetadataProcessor moduleMetadataProcessor;
    private final DependencySubstitutionRules globalDependencySubstitutionRule;

    public DefaultGlobalDependencyResolutionRules(ComponentMetadataProcessorFactory componentMetadataProcessorFactory,
                                                  ComponentModuleMetadataProcessor moduleMetadataProcessor,
                                                  List<DependencySubstitutionRules> ruleProviders) {
        this.componentMetadataProcessorFactory = componentMetadataProcessorFactory;
        this.moduleMetadataProcessor = moduleMetadataProcessor;
        this.globalDependencySubstitutionRule = new CompositeDependencySubstitutionRules(ruleProviders);
    }

    @Override
    public ComponentMetadataProcessorFactory getComponentMetadataProcessorFactory() {
        return componentMetadataProcessorFactory;
    }

    @Override
    public ComponentModuleMetadataProcessor getModuleMetadataProcessor() {
        return moduleMetadataProcessor;
    }

    @Override
    public DependencySubstitutionRules getDependencySubstitutionRules() {
        return globalDependencySubstitutionRule;
    }

    private static class CompositeDependencySubstitutionRules implements DependencySubstitutionRules {
        private final List<DependencySubstitutionRules> ruleProviders;

        private CompositeDependencySubstitutionRules(List<DependencySubstitutionRules> ruleProviders) {
            this.ruleProviders = ruleProviders;
        }

        @Override
        public Action<DependencySubstitution> getRuleAction() {
            return Actions.composite(CollectionUtils.collect(ruleProviders, new Transformer<Action<? super DependencySubstitution>, DependencySubstitutionRules>() {
                @Override
                public Action<? super DependencySubstitution> transform(DependencySubstitutionRules rule) {
                    return rule.getRuleAction();
                }
            }));
        }

        @Override
        public boolean hasRules() {
            for (DependencySubstitutionRules ruleProvider : ruleProviders) {
                if (ruleProvider.hasRules()) {
                    return true;
                }
            }
            return false;
        }
    }
}
