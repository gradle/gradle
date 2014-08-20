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
        PluginDescriptor qualifiedDescriptor = super.findPluginDescriptor(qualified, classLoader);
        if (qualifiedDescriptor != null || qualified.equals(pluginId)) {
            return qualifiedDescriptor;
        }

        // Try to load the plugin unqualified.
        // This is only applicable when users are unit testing plugins.
        // What happens there is that the plugin descriptor and class are loaded via the system classloader (i.e. they are on the test classpath)
        // The classloader we are given here has full visibility of the test classpath (it's the plugins class loader, which is just the runtime classloader in this case)
        // If we don't find the plugin at this level, the child plugin registry will find the descriptor but not the class.
        // This is because that registry is based on the API classloader, which allows gradle-plugins/ resources, but restricts the packages that can be loaded.
        // The end result is a ClassNotFoundException.
        // Therefore, try for the plugin unqualified in order to load the plugin under test here during unit testing.
        // A better solution would be to allow the API classloader to only see core plugin descriptors, and to add the test classpath to the Project object under test's class loader scope.

        return super.findPluginDescriptor(pluginId, classLoader);
    }

    private String maybeQualify(String id) {
        if (id.startsWith(CORE_PLUGIN_PREFIX)) {
            return id;
        } else {
            return CORE_PLUGIN_PREFIX + id;
        }
    }

}
