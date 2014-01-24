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

package org.gradle.plugin.resolve.internal;

import org.gradle.api.internal.plugins.PluginDescriptor;
import org.gradle.api.internal.plugins.PluginDescriptorLocator;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.plugins.UnknownPluginException;

public class NotInPluginRegistryPluginResolverCheck implements PluginResolver {

    private final PluginResolver delegate;
    private final PluginRegistry corePluginRegistry;
    private final PluginDescriptorLocator pluginDescriptorLocator;

    public NotInPluginRegistryPluginResolverCheck(PluginResolver delegate, PluginRegistry corePluginRegistry, PluginDescriptorLocator pluginDescriptorLocator) {
        this.delegate = delegate;
        this.corePluginRegistry = corePluginRegistry;
        this.pluginDescriptorLocator = pluginDescriptorLocator;
    }

    public PluginResolution resolve(PluginRequest pluginRequest) {
        String pluginId = pluginRequest.getId();
        PluginDescriptor pluginDescriptor = pluginDescriptorLocator.findPluginDescriptor(pluginId);
        if (pluginDescriptor == null || isCorePlugin(pluginId)) {
            return delegate.resolve(pluginRequest);
        } else {
            throw new InvalidPluginRequestException(
                    String.format("Plugin '%s' is already on the script classpath (plugins on the script classpath cannot be used in a plugins {} block; move \"apply plugin: '%s'\" outside of the plugins {} block)", pluginId, pluginId)
            );
        }
    }

    private boolean isCorePlugin(String pluginId) {
        try {
            corePluginRegistry.getTypeForId(pluginId);
            return true;
        } catch (UnknownPluginException ignore) {
            return false;
        }
    }

    public String getDescriptionForNotFoundMessage() {
        return delegate.getDescriptionForNotFoundMessage();
    }

}
