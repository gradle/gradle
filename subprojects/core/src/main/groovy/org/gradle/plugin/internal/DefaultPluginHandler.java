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
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.internal.DefaultNamedDomainObjectList;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugin.resolve.internal.DefaultPluginRequest;
import org.gradle.plugin.resolve.internal.PluginRequest;
import org.gradle.plugin.resolve.internal.PluginResolution;
import org.gradle.plugin.resolve.internal.PluginResolver;
import org.gradle.util.CollectionUtils;

public class DefaultPluginHandler implements PluginHandlerInternal {

    private final Action<? super PluginResolution> pluginResolutionHandler;
    private final NamedDomainObjectList<PluginResolver> repositories;

    public DefaultPluginHandler(Instantiator instantiator, Action<? super PluginResolution> pluginResolutionHandler) {
        this.pluginResolutionHandler = pluginResolutionHandler;

        @SuppressWarnings("unchecked")
        DefaultNamedDomainObjectList<PluginResolver> unchecked = instantiator.newInstance(DefaultNamedDomainObjectList.class, PluginResolver.class, instantiator, Named.Namer.forType(PluginResolver.class));
        this.repositories = unchecked;
    }

    public void apply(String pluginId) {
       apply(new DefaultPluginRequest(pluginId));
    }

    public void apply(String pluginId, String version) {
        apply(new DefaultPluginRequest(pluginId, version));
    }

    private void apply(PluginRequest request) {
        PluginResolution resolution = null;
        for (PluginResolver repository : repositories) {
            resolution = repository.resolve(request);
            if (resolution != null) {
                break;
            }
        }

        if (resolution == null) {
            throw new UnknownPluginException("Cannot resolve plugin request " + request + " from repositories: " + CollectionUtils.toStringList(repositories));
        }

        pluginResolutionHandler.execute(resolution);
    }

    public NamedDomainObjectList<PluginResolver> getResolvers() {
        return repositories;
    }
}
