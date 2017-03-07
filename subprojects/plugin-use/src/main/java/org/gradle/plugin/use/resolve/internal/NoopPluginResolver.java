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

package org.gradle.plugin.use.resolve.internal;

import org.gradle.api.Plugin;
import org.gradle.api.internal.plugins.DefaultPotentialPluginWithId;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;
import org.gradle.plugin.management.internal.InvalidPluginRequestException;
import org.gradle.plugin.management.internal.PluginRequestInternal;

// Used for testing the plugins DSL
public class NoopPluginResolver implements PluginResolver {

    public static final PluginId NOOP_PLUGIN_ID = DefaultPluginId.of("noop");
    private final PluginRegistry pluginRegistry;

    public NoopPluginResolver(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    public void resolve(PluginRequestInternal pluginRequest, PluginResolutionResult result) throws InvalidPluginRequestException {
        if (pluginRequest.getId().equals(NOOP_PLUGIN_ID)) {
            result.found("noop resolver", new SimplePluginResolution(DefaultPotentialPluginWithId.of(NOOP_PLUGIN_ID, pluginRegistry.inspect(NoopPlugin.class))));
        }
    }

    public static class NoopPlugin implements Plugin<Object> {
        public void apply(Object target) {
            // do nothing
        }
    }

}
