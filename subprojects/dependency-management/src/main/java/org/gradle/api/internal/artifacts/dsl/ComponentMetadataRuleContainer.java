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
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.NoOpDerivationStrategy;
import org.gradle.internal.component.external.model.VariantDerivationStrategy;
import org.gradle.internal.rules.SpecRuleAction;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Container for registered ComponentMetadataRules, either class based or closure / action based.
 */
class ComponentMetadataRuleContainer implements Iterable<MetadataRuleWrapper> {
    private final List<MetadataRuleWrapper> rules = Lists.newArrayListWithExpectedSize(10);
    private MetadataRuleWrapper lastAdded;
    private boolean classBasedRulesOnly = true;
    private VariantDerivationStrategy variantDerivationStrategy = NoOpDerivationStrategy.getInstance();
    private int rulesHash = 0;
    private Consumer<DisplayName> onAdd;

    void addRule(SpecRuleAction<? super ComponentMetadataDetails> ruleAction) {
        lastAdded = new ActionBasedMetadataRuleWrapper(ruleAction);
        addRule();
        classBasedRulesOnly = false;
        rulesHash = 31 * rulesHash + ruleAction.hashCode();
    }

    private void addRule() {
        if (onAdd != null) {
            onAdd.accept(lastAdded.getDisplayName());
        }
        rules.add(lastAdded);
    }

    void addClassRule(SpecConfigurableRule ruleAction) {
        if (lastAdded != null && lastAdded.isClassBased()) {
            lastAdded.addClassRule(ruleAction);
        } else {
            lastAdded = new ClassBasedMetadataRuleWrapper(ruleAction);
            addRule();
        }
        rulesHash = 31 * rulesHash + ruleAction.getConfigurableRule().hashCode();
    }

    boolean isClassBasedRulesOnly() {
        return classBasedRulesOnly;
    }

    boolean isEmpty() {
        return rules.isEmpty();
    }

    @Override
    public Iterator<MetadataRuleWrapper> iterator() {
        return rules.iterator();
    }

    Collection<SpecConfigurableRule> getOnlyClassRules() {
        if (!isClassBasedRulesOnly() || isEmpty()) {
            throw new IllegalStateException("This method cannot be used unless there is at least one rule and they are all class based");
        }
        return rules.get(0).getClassRules();
    }

    public VariantDerivationStrategy getVariantDerivationStrategy() {
        return variantDerivationStrategy;
    }

    public void setVariantDerivationStrategy(VariantDerivationStrategy variantDerivationStrategy) {
        this.variantDerivationStrategy = variantDerivationStrategy;
    }

    public int getRulesHash() {
        return 31 * variantDerivationStrategy.hashCode() + rulesHash;
    }

    void onAddRule(Consumer<DisplayName> consumer) {
        this.onAdd = consumer;
    }
}
