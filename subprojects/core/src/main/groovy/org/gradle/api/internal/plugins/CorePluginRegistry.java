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

import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugin.internal.PluginId;

public class CorePluginRegistry extends DefaultPluginRegistry {

    public static final String CORE_PLUGIN_NAMESPACE = "org" + PluginId.SEPARATOR + "gradle";
    public static final String CORE_PLUGIN_PREFIX = CORE_PLUGIN_NAMESPACE + PluginId.SEPARATOR;

    public CorePluginRegistry(ClassLoader classLoader, Instantiator instantiator) {
        super(classLoader, instantiator);
    }

    @Override
    protected PluginDescriptor findPluginDescriptor(String pluginId, ClassLoader classLoader) {
        String qualified = maybeQualify(pluginId);
        return super.findPluginDescriptor(qualified, classLoader);
    }

    private String maybeQualify(String id) {
        if (id.startsWith(CORE_PLUGIN_PREFIX)) {
            return id;
        } else {
            return CORE_PLUGIN_PREFIX + id;
        }
    }

}
