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

import org.gradle.api.DomainObjectCollection;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Plugin;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.plugins.PluginAware;
import org.gradle.api.specs.Spec;
import org.gradle.model.internal.inspect.ModelRuleSourceDetector;

public abstract class AbstractAppliedPluginContainer implements PluginApplicationHandler, AppliedPluginContainer {

    private final DomainObjectSet<Class<? extends Object>> appliedPlugins;
    private final PluginRegistry pluginRegistry;
    protected final ModelRuleSourceDetector modelRuleSourceDetector;
    protected final PluginAware target;

    abstract protected void extractModelRules(Class<?> pluginClass);

    public AbstractAppliedPluginContainer(PluginAware target, PluginRegistry pluginRegistry, ModelRuleSourceDetector modelRuleSourceDetector) {
        this.target = target;
        this.pluginRegistry = pluginRegistry;
        this.modelRuleSourceDetector = modelRuleSourceDetector;
        @SuppressWarnings("unchecked") Class<? extends Class<?>> type = (Class<? extends Class<?>>) Class.class.getClass();
        appliedPlugins = new DefaultDomainObjectSet<Class<?>>(type);
    }

    private void applyValidType(Class<?> pluginClass) {
        if (appliedPlugins.add(pluginClass)) {
            if (Plugin.class.isAssignableFrom(pluginClass)) {
                @SuppressWarnings("unchecked") Class<? extends Plugin> pluginImplementingClass = (Class<? extends Plugin>) pluginClass;
                target.getPlugins().apply(pluginImplementingClass);
            }

            extractModelRules(pluginClass);
        }
    }

    public void apply(Class<?> pluginClass) {
        if (!Plugin.class.isAssignableFrom(pluginClass) && !modelRuleSourceDetector.hasModelSources(pluginClass)) {
            throw new IllegalArgumentException(String.format("'%s' is neither a plugin or a rule source and cannot be applied.", pluginClass.getName()));
        }
        applyValidType(pluginClass);
    }

    public void apply(String pluginId) {
        applyValidType(pluginRegistry.getTypeForId(pluginId));
    }

    public boolean contains(Class<?> pluginClass) {
        return appliedPlugins.contains(pluginClass);
    }

    public DomainObjectCollection<Class<?>> matching(Spec<? super Class<?>> spec) {
        return appliedPlugins.matching(spec);
    }
}
