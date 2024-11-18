/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRules;
import org.gradle.internal.Actions;
import org.gradle.util.internal.CollectionUtils;

import javax.inject.Inject;
import java.util.List;

public class GlobalDependencyResolutionRules {
    private final DependencySubstitutionRules compositeRule;

    @Inject
    public GlobalDependencyResolutionRules(List<DependencySubstitutionRules> ruleProviders) {
        this.compositeRule = new CompositeSubstitutionRules(ruleProviders);
    }

    public DependencySubstitutionRules getDependencySubstitutionRules() {
        return compositeRule;
    }

    private static class CompositeSubstitutionRules implements DependencySubstitutionRules {
        private final List<DependencySubstitutionRules> ruleProviders;

        @Inject
        public CompositeSubstitutionRules(List<DependencySubstitutionRules> ruleProviders) {
            this.ruleProviders = ruleProviders;
        }

        @Override
        public Action<DependencySubstitution> getRuleAction() {
            return Actions.composite(CollectionUtils.collect(ruleProviders, DependencySubstitutionRules::getRuleAction));
        }

        @Override
        public boolean rulesMayAddProjectDependency() {
            for (DependencySubstitutionRules ruleProvider : ruleProviders) {
                if (ruleProvider.rulesMayAddProjectDependency()) {
                    return true;
                }
            }
            return false;
        }
    }

}
