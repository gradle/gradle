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

package org.gradle.plugin.use.resolve.internal;

import org.gradle.api.internal.plugins.PluginDescriptor;
import org.gradle.api.internal.plugins.PluginDescriptorLocator;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.plugin.management.internal.InvalidPluginRequestException;
import org.gradle.plugin.use.PluginId;

public class NotNonCorePluginOnClasspathCheckPluginResolver implements PluginResolver {

    private final PluginResolver delegate;
    private final PluginRegistry corePluginRegistry;
    private final PluginDescriptorLocator pluginDescriptorLocator;

    public NotNonCorePluginOnClasspathCheckPluginResolver(PluginResolver delegate, PluginRegistry corePluginRegistry, PluginDescriptorLocator pluginDescriptorLocator) {
        this.delegate = delegate;
        this.corePluginRegistry = corePluginRegistry;
        this.pluginDescriptorLocator = pluginDescriptorLocator;
    }

    public void resolve(ContextAwarePluginRequest pluginRequest, PluginResolutionResult result) {
        PluginId pluginId = pluginRequest.getId();
        if (pluginId == null) {
            delegate.resolve(pluginRequest, result);
        } else {
            PluginDescriptor pluginDescriptor = pluginDescriptorLocator.findPluginDescriptor(pluginId.toString());
            if (pluginDescriptor == null || isCorePlugin(pluginId)) {
                delegate.resolve(pluginRequest, result);
            } else {
                throw new InvalidPluginRequestException(pluginRequest, pluginOnClasspathErrorMessage(pluginId.toString()));
            }
        }
    }

    public static String pluginOnClasspathErrorMessage(String pluginId) {
        return String.format("Plugin '%s' is already on the script classpath. Plugins on the script classpath cannot be applied in the plugins {} block. Add  \"apply plugin: '%s'\" to the body of the script to use the plugin.", pluginId, pluginId);
    }

    private boolean isCorePlugin(PluginId pluginId) {
        return corePluginRegistry.lookup(pluginId) != null;
    }

}
