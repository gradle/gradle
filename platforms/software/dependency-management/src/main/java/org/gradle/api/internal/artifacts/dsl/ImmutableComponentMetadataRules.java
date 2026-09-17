/*
 * Copyright 2026 the original author or authors.
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
import org.gradle.internal.rules.SpecRuleAction;

public class ImmutableComponentMetadataRules {

    public static final ImmutableComponentMetadataRules EMPTY = new ImmutableComponentMetadataRules(ImmutableList.of());

    private final ImmutableList<ImmutableRule> rules;
    private final int rulesHash;

    ImmutableComponentMetadataRules(ImmutableList<ImmutableRule> rules) {
        this.rules = rules;
        this.rulesHash = computeRulesHash(rules);
    }

    public ImmutableList<ImmutableRule> getRules() {
        return rules;
    }

    public int getRulesHash() {
        return rulesHash;
    }

    private static int computeRulesHash(ImmutableList<ImmutableRule> rules) {
        int hash = 0;
        for (ImmutableRule rule : rules) {
            if (rule instanceof ImmutableRule.ClassBased classBased) {
                for (SpecConfigurableRule classRule : classBased.classRules()) {
                    hash = 31 * hash + classRule.getConfigurableRule().hashCode();
                }
            } else if (rule instanceof ImmutableRule.ActionBased actionBased) {
                hash = 31 * hash + actionBased.rule().hashCode();
            }
        }
        return hash;
    }

    public sealed interface ImmutableRule {
        record ClassBased(ImmutableList<SpecConfigurableRule> classRules) implements ImmutableRule {}
        record ActionBased(SpecRuleAction<? super ComponentMetadataDetails> rule) implements ImmutableRule {}
    }

}
