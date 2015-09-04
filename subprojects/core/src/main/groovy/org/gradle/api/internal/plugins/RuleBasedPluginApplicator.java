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

package org.gradle.api.internal.plugins;

import org.gradle.api.Nullable;
import org.gradle.api.Plugin;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.ExtractedModelRule;
import org.gradle.model.internal.inspect.ModelRuleExtractor;
import org.gradle.model.internal.inspect.ModelRuleSourceDetector;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.registry.ModelRegistryScope;

public class RuleBasedPluginApplicator<T extends ModelRegistryScope & PluginAwareInternal> implements PluginApplicator {

    private final T target;
    private final PluginApplicator imperativeApplicator;
    private final ModelRuleExtractor ruleInspector;
    private final ModelRuleSourceDetector ruleDetector;

    public RuleBasedPluginApplicator(T target, ModelRuleExtractor ruleInspector, ModelRuleSourceDetector ruleDetector) {
        this.target = target;
        this.ruleInspector = ruleInspector;
        this.ruleDetector = ruleDetector;
        this.imperativeApplicator = new ImperativeOnlyPluginApplicator<T>(target);
    }

    public void applyImperative(@Nullable String pluginId, Plugin<?> plugin) {
        imperativeApplicator.applyImperative(pluginId, plugin);
    }

    public void applyRules(@Nullable String pluginId, Class<?> clazz) {
        ModelRegistry modelRegistry = target.getModelRegistry();
        Iterable<Class<? extends RuleSource>> declaredSources = ruleDetector.getDeclaredSources(clazz);
        for (Class<? extends RuleSource> ruleSource : declaredSources) {
            Iterable<ExtractedModelRule> rules = ruleInspector.extract(ruleSource);
            for (ExtractedModelRule rule : rules) {
                for (Class<?> dependency : rule.getRuleDependencies()) {
                    target.getPluginManager().apply(dependency);
                }

                if (rule.getType().equals(ExtractedModelRule.Type.ACTION)) {
                    modelRegistry.configure(rule.getActionRole(), rule.getAction());
                } else if (rule.getType().equals(ExtractedModelRule.Type.CREATOR)) {
                    modelRegistry.create(rule.getCreator());
                } else if (!rule.getType().equals(ExtractedModelRule.Type.DEPENDENCIES)) {
                    throw new IllegalStateException("unhandled extracted model rule type: " + rule.getType());
                }
            }
        }
    }

    public void applyImperativeRulesHybrid(@Nullable String pluginId, Plugin<?> plugin) {
        applyImperative(pluginId, plugin);
        applyRules(pluginId, plugin.getClass());
    }

}
