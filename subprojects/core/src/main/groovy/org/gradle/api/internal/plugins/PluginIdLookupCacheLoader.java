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

import com.google.common.cache.CacheLoader;
import org.gradle.api.plugins.UnknownPluginException;

class PluginIdLookupCacheLoader extends CacheLoader<PluginIdLookupCacheKey, Boolean> {

    private final PluginRegistry pluginRegistry;

    PluginIdLookupCacheLoader(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    @Override
    public Boolean load(@SuppressWarnings("NullableProblems") PluginIdLookupCacheKey key) throws Exception {
        Class<?> pluginClass = key.getPluginClass();

        // Plugin registry will have the mapping cached in memory for most plugins, try first
        try {
            Class<?> typeForId = pluginRegistry.getTypeForId(key.getId());
            if (typeForId.equals(pluginClass)) {
                return true;
            }
        } catch (UnknownPluginException ignore) {
            // ignore
        }

        PluginDescriptorLocator locator = new ClassloaderBackedPluginDescriptorLocator(pluginClass.getClassLoader());
        PluginDescriptor pluginDescriptor = locator.findPluginDescriptor(key.getId());
        return pluginDescriptor != null && pluginDescriptor.getImplementationClassName().equals(pluginClass.getName());
    }
}
