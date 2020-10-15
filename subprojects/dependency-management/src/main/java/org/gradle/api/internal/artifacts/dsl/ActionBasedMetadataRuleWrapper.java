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

import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.rules.SpecRuleAction;

import java.util.Collection;

class ActionBasedMetadataRuleWrapper implements MetadataRuleWrapper {
    private final SpecRuleAction<? super ComponentMetadataDetails> ruleAction;

    ActionBasedMetadataRuleWrapper(SpecRuleAction<? super ComponentMetadataDetails> ruleAction) {
        this.ruleAction = ruleAction;
    }

    @Override
    public DisplayName getDisplayName() {
        return Describables.of("opaque inline rule");
    }

    @Override
    public boolean isClassBased() {
        return false;
    }

    @Override
    public Collection<SpecConfigurableRule> getClassRules() {
        throw new UnsupportedOperationException("This operation is not supported by this implementation");
    }

    @Override
    public void addClassRule(SpecConfigurableRule ruleAction) {
        throw new UnsupportedOperationException("This operation is not supported by this implementation");
    }

    @Override
    public SpecRuleAction<? super ComponentMetadataDetails> getRule() {
        return ruleAction;
    }
}
