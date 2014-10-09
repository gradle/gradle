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
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.model.internal.inspect.ModelRuleInspector;
import org.gradle.model.internal.inspect.ModelRuleSourceDetector;
import org.gradle.model.internal.inspect.RuleSourceDependencies;

import java.util.Set;

public class ProjectAppliedPlugins implements AppliedPluginsInternal {

    private final ModelRuleInspector inspector;
    private final PluginRegistry pluginRegistry;
    private final ProjectInternal target;

    public ProjectAppliedPlugins(ProjectInternal target, PluginRegistry pluginRegistry, ModelRuleInspector inspector) {
        this.pluginRegistry = pluginRegistry;
        this.target = target;
        this.inspector = inspector;
    }

    private Set<Class<?>> getDeclaredSources(Class<?> pluginClass) {
        ModelRuleSourceDetector detector = new ModelRuleSourceDetector();
        return detector.getDeclaredSources(pluginClass);
    }

    private void extractModelRules(Set<Class<?>> declaredSources) {
        for (Class<?> source : declaredSources) {
            inspector.inspect(source, target.getModelRegistry(), new RuleSourceDependencies() {
                public void add(Class<?> source) {
                    if (!Plugin.class.isAssignableFrom(source)) {
                        throw new IllegalArgumentException("Only plugin classes are valid as rule source dependencies.");
                    }
                    @SuppressWarnings("unchecked") Class<? extends Plugin> pluginImplementingClass = (Class<? extends Plugin>) source;
                    target.getPlugins().apply(pluginImplementingClass);
                }
            });
        }
    }

    public void extractModelRulesAndAdd(final Class<?> pluginClass) {
        extractModelRules(getDeclaredSources(pluginClass));
    }

    public void apply(Class<?> pluginClass) {
        if (Plugin.class.isAssignableFrom(pluginClass)) {
            @SuppressWarnings("unchecked") Class<? extends Plugin<?>> pluginImplementingClass = (Class<? extends Plugin<?>>) pluginClass;
            target.getPlugins().apply(pluginImplementingClass);
        } else {
            Set<Class<?>> declaredSources = getDeclaredSources(pluginClass);
            if (declaredSources.size() == 0) {
                throw new IllegalArgumentException(String.format("%s is neither a plugin or a rule source and cannot be applied.", pluginClass.getName()));
            }
            extractModelRules(declaredSources);
        }
    }

    public void apply(String pluginId) {
        apply(pluginRegistry.getTypeForId(pluginId));
    }
}
