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

package org.gradle.api.internal.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.plugins.PluginAware;
import org.gradle.model.internal.inspect.RuleSourceDependencies;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.registry.ModelRegistryScope;
import org.gradle.model.internal.inspect.ModelRuleInspector;

import java.util.Set;

public class PluginModelRuleExtractor implements PluginApplicationAction {

    private final ModelRuleInspector inspector;

    public PluginModelRuleExtractor(ModelRuleInspector inspector) {
        this.inspector = inspector;
    }

    public void execute(PluginApplication pluginApplication) {
        Class<? extends Plugin> pluginClass = pluginApplication.getPlugin().getClass();
        Set<Class<?>> sources = inspector.getDeclaredSources(pluginClass);
        if (!sources.isEmpty()) {
            PluginAware target = pluginApplication.getTarget();
            if (!(target instanceof ModelRegistryScope)) {
                throw new UnsupportedOperationException(String.format("Cannot apply model rules of plugin '%s' as the target '%s' is not model rule aware", pluginClass.getName(), target));
            }

            ModelRegistry modelRegistry = ((ModelRegistryScope) target).getModelRegistry();
            for (Class<?> source : sources) {
                inspector.inspect(source, modelRegistry, new PluginRuleSourceDependencies(target));
            }
        }
    }

    private static class PluginRuleSourceDependencies implements RuleSourceDependencies {
        private final PluginAware plugins;

        private PluginRuleSourceDependencies(PluginAware plugins) {
            this.plugins = plugins;
        }

        public void add(Class<?> source) {
            if (!Plugin.class.isAssignableFrom(source)) {
                throw new IllegalArgumentException("Only plugin classes are valid as rule source dependencies.");
            }
            plugins.getPlugins().apply((Class<? extends Plugin>) source);
        }
    }

}
