/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.Action;
import org.gradle.api.plugins.PluginCollection;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.internal.DefaultDomainObjectContainer;
import org.gradle.api.internal.AbstractDomainObjectCollection;
import groovy.lang.Closure;

/**
 * @author Hans Dockter
 */
public abstract class AbstractPluginContainer extends DefaultPluginCollection<Plugin> implements PluginContainer {
    public AbstractPluginContainer() {
        super(Plugin.class);
    }

    protected Plugin addPlugin(String id, PluginProvider pluginProvider) {
        return addPluginInternal(getTypeForId(id), id, pluginProvider);
    }

    protected <T extends Plugin> T addPlugin(Class<T> type, PluginProvider pluginProvider) {
        return addPluginInternal(type, getNameForType(type), pluginProvider);
    }

    protected <T extends Plugin> T addPluginInternal(Class<T> type, String name, PluginProvider pluginProvider) {
        if (findByName(name) == null) {
            Plugin plugin = pluginProvider.providePlugin(type);
            addObject(name, plugin);
        }
        return (T) findByName(name);
    }

    protected abstract String getNameForType(Class<? extends Plugin> type);

    protected abstract Class<? extends Plugin> getTypeForId(String id);

    public boolean hasPlugin(String name) {
        return findPlugin(name) != null;
    }

    public boolean hasPlugin(Class<? extends Plugin> type) {
        return findPlugin(type) != null;   
    }

    public Plugin findPlugin(String name) {
        return findByName(name);
    }

    public Plugin findPlugin(Class<? extends Plugin> type) {
        return findByName(getNameForType(type));
    }

    protected interface PluginProvider {
        Plugin providePlugin(Class<? extends Plugin> type);
    }
}
