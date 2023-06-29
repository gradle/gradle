/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.plugins.PluginDescriptorLocator;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.use.PluginId;

@NonNullApi
public class AlreadyOnClasspathIgnoringPluginResolver implements PluginResolver {
    private final PluginResolver delegate;
    private final PluginDescriptorLocator pluginDescriptorLocator;

    public AlreadyOnClasspathIgnoringPluginResolver(
        PluginResolver delegate,
        PluginDescriptorLocator pluginDescriptorLocator
    ) {
        this.delegate = delegate;
        this.pluginDescriptorLocator = pluginDescriptorLocator;
    }

    @Override
    public void resolve(PluginRequestInternal pluginRequest, PluginResolutionResult result) {
        PluginId pluginId = pluginRequest.getId();
        if (!isPresentOnClasspath(pluginId)) {
            delegate.resolve(pluginRequest, result);
        } else {
            result.alreadyApplied("Already on classpath");
        }
    }

    private boolean isPresentOnClasspath(PluginId pluginId) {
        return pluginDescriptorLocator.findPluginDescriptor(pluginId.toString()) != null;
    }

}
