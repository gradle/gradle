/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl;

import com.google.common.collect.Lists;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.action.ConfigurableRule;
import org.gradle.internal.rules.SpecRuleAction;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

class ClassBasedMetadataRuleWrapper implements MetadataRuleWrapper {
    private final List<SpecConfigurableRule> classRules = Lists.newArrayListWithExpectedSize(5);

    ClassBasedMetadataRuleWrapper(SpecConfigurableRule classRule) {
        this.classRules.add(classRule);
    }

    @Override
    public boolean isClassBased() {
        return true;
    }

    @Override
    public Collection<SpecConfigurableRule> getClassRules() {
        return classRules;
    }

    @Override
    public void addClassRule(SpecConfigurableRule classRule) {
        classRules.add(classRule);
    }

    @Override
    public SpecRuleAction<? super ComponentMetadataDetails> getRule() {
        throw new UnsupportedOperationException("This operation is not supported by this implementation");
    }

    @Override
    public DisplayName getDisplayName() {
        return Describables.of(classRules.stream()
            .map(SpecConfigurableRule::getConfigurableRule)
            .map(ConfigurableRule::getRuleClass)
            .map(Class::getName)
            .collect(Collectors.joining(",")));
    }
}
