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

package org.gradle.plugin.internal;

import org.gradle.api.Action;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.typeconversion.MapKey;
import org.gradle.internal.typeconversion.MapNotationParser;
import org.gradle.plugin.PluginHandler;
import org.gradle.plugin.resolve.internal.DefaultPluginRequest;
import org.gradle.plugin.resolve.internal.PluginRequest;
import org.gradle.plugin.resolve.internal.PluginResolution;
import org.gradle.plugin.resolve.internal.PluginResolver;

import java.util.Map;

public class DefaultPluginHandler implements PluginHandler {

    private final PluginResolver pluginResolver;
    private final Action<? super PluginResolution> pluginResolutionHandler;

    public DefaultPluginHandler(PluginResolver pluginResolver, Action<? super PluginResolution> pluginResolutionHandler) {
        this.pluginResolver = pluginResolver;
        this.pluginResolutionHandler = pluginResolutionHandler;
    }

    private static class PluginRequestNotationParser extends MapNotationParser<PluginRequest> {
        protected PluginRequest parseMap(@MapKey("plugin") String id, @MapKey("version") @Optional String version) {
            return version == null ? new DefaultPluginRequest(id) : new DefaultPluginRequest(id, version);
        }
    }

    public void apply(Map<String, ?> attributes) {
        PluginRequest pluginRequest = new PluginRequestNotationParser().parseType(attributes);
        apply(pluginRequest);
    }

    private void apply(PluginRequest request) {
        PluginResolution resolution = pluginResolver.resolve(request);
        if (resolution == null) {
            throw new UnknownPluginException("Cannot resolve plugin request " + request + " from plugin resolver: " + pluginResolver);
        }

        pluginResolutionHandler.execute(resolution);
    }

}
