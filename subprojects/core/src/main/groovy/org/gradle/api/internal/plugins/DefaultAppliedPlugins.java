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

import org.gradle.api.Plugin;
import org.gradle.api.plugins.PluginAware;
import org.gradle.model.internal.inspect.ModelRuleSourceDetector;

public class DefaultAppliedPlugins implements AppliedPluginsInternal {

    private final PluginAware target;
    protected final PluginRegistry pluginRegistry;

    public DefaultAppliedPlugins(PluginAware target, PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
        this.target = target;
    }

    public void extractModelRulesAndAdd(Class<?> pluginClass) {
        ModelRuleSourceDetector detector = new ModelRuleSourceDetector();
        if (detector.getDeclaredSources(pluginClass).size() > 0) {
            throw new UnsupportedOperationException(String.format("Cannot apply model rules of plugin '%s' as the target '%s' is not model rule aware", pluginClass.getName(), target));
        }
    }

    public void apply(Class<?> pluginClass) {
        if (!Plugin.class.isAssignableFrom(pluginClass)) {
            throw new UnsupportedOperationException(String.format("'%s' does not implement the Plugin interface and only classes that implement it can be applied to '%s'", pluginClass.getName(), target));
        }
        @SuppressWarnings("unchecked") Class<? extends Plugin<?>> pluginImplementingClass = (Class<? extends Plugin<?>>) pluginClass;
        target.getPlugins().apply(pluginImplementingClass);
    }

    public void apply(String pluginId) {
        apply(pluginRegistry.getTypeForId(pluginId));
    }
}
