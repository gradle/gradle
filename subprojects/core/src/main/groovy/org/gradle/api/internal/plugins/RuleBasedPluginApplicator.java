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
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelRuleSourceApplicator;
import org.gradle.model.internal.registry.ModelRegistryScope;

public class RuleBasedPluginApplicator<T extends ModelRegistryScope & PluginAwareInternal> implements PluginApplicator {

    private final T target;
    private final PluginApplicator imperativeApplicator;
    private final ModelRuleSourceApplicator modelRuleSourceApplicator;

    public RuleBasedPluginApplicator(T target, ModelRuleSourceApplicator modelRuleSourceApplicator) {
        this.target = target;
        this.modelRuleSourceApplicator = modelRuleSourceApplicator;
        this.imperativeApplicator = new ImperativeOnlyPluginApplicator<T>(target);
    }

    public void applyImperative(@Nullable String pluginId, Plugin<?> plugin) {
        imperativeApplicator.applyImperative(pluginId, plugin);
    }

    public void applyRules(@Nullable String pluginId, Class<?> clazz) {
        modelRuleSourceApplicator.apply(clazz, ModelPath.ROOT, target.getModelRegistry(), target.getPluginManager());
    }

    public void applyImperativeRulesHybrid(@Nullable String pluginId, Plugin<?> plugin) {
        applyImperative(pluginId, plugin);
        applyRules(pluginId, plugin.getClass());
    }

}
