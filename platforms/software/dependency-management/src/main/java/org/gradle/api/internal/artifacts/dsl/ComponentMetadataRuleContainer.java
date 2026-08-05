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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.internal.DisplayName;
import org.gradle.internal.rules.SpecRuleAction;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Mutable builder for class-based and action-based registered ComponentMetadataRules. Rules are
 * kept in registration order so that they are applied in that order, with adjacent class rules
 * merged into a single wrapper.
 */
public class ComponentMetadataRuleContainer {

    private final List<MetadataRuleWrapper> rules = new ArrayList<>(10);
    private @Nullable Consumer<DisplayName> onAdd;

    void addRule(SpecRuleAction<? super ComponentMetadataDetails> ruleAction) {
        addWrapper(new ActionBasedMetadataRuleWrapper(ruleAction));
    }

    void addClassRule(SpecConfigurableRule ruleAction) {
        if (rules.isEmpty()) {
            addWrapper(new ClassBasedMetadataRuleWrapper(ruleAction));
        } else {
            MetadataRuleWrapper last = rules.get(rules.size() - 1);
            if (last.isClassBased()) {
                // Merge consecutive class rules into a single wrapper so they can be batched into one cacheable action.
                last.addClassRule(ruleAction);
            } else {
                addWrapper(new ClassBasedMetadataRuleWrapper(ruleAction));
            }
        }
    }

    private void addWrapper(MetadataRuleWrapper wrapper) {
        if (onAdd != null) {
            onAdd.accept(wrapper.getDisplayName());
        }
        rules.add(wrapper);
    }

    void onAddRule(Consumer<DisplayName> consumer) {
        this.onAdd = consumer;
    }

    public ImmutableComponentMetadataRules asImmutable() {
        if (rules.isEmpty()) {
            return ImmutableComponentMetadataRules.EMPTY;
        }

        ImmutableList.Builder<ImmutableComponentMetadataRules.ImmutableRule> immutableRules = ImmutableList.builderWithExpectedSize(rules.size());
        for (MetadataRuleWrapper wrapper : rules) {
            if (wrapper.isClassBased()) {
                immutableRules.add(new ImmutableComponentMetadataRules.ImmutableRule.ClassBased(ImmutableList.copyOf(wrapper.getClassRules())));
            } else {
                immutableRules.add(new ImmutableComponentMetadataRules.ImmutableRule.ActionBased(wrapper.getRule()));
            }
        }
        return new ImmutableComponentMetadataRules(immutableRules.build());
    }

}
