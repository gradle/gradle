/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.initialization.definition;

import com.google.common.collect.Lists;
import org.gradle.api.Transformer;
import org.gradle.api.initialization.definition.InjectedPluginDependencies;
import org.gradle.api.initialization.definition.InjectedPluginDependency;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.plugin.management.internal.DefaultPluginRequests;
import org.gradle.plugin.management.internal.InvalidPluginRequestException;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.use.PluginId;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.gradle.util.CollectionUtils.collect;

public class DefaultInjectedPluginDependencies implements InjectedPluginDependencies {
    private final List<DefaultInjectedPluginDependency> dependencies = Lists.newArrayList();
    private final ClassLoaderScope classLoaderScope;

    public DefaultInjectedPluginDependencies(ClassLoaderScope classLoaderScope) {
        this.classLoaderScope = classLoaderScope;
    }

    @Override
    public InjectedPluginDependency id(String id) {
        DefaultInjectedPluginDependency injectedPluginDependency = new DefaultInjectedPluginDependency(id);
        dependencies.add(injectedPluginDependency);
        return injectedPluginDependency;
    }

    public PluginRequests getRequests() {
        if (dependencies.isEmpty()) {
            return DefaultPluginRequests.EMPTY;
        }
        return new DefaultPluginRequests(listPluginRequests());
    }

    List<PluginRequestInternal> listPluginRequests() {
        List<PluginRequestInternal> pluginRequests = collect(dependencies, new Transformer<PluginRequestInternal, DefaultInjectedPluginDependency>() {
            public PluginRequestInternal transform(DefaultInjectedPluginDependency original) {
                return new SelfResolvingPluginRequest(original.getId(), classLoaderScope);
            }
        });

        Map<PluginId, Collection<PluginRequestInternal>> groupedById = CollectionUtils.groupBy(pluginRequests, new Transformer<PluginId, PluginRequestInternal>() {
            public PluginId transform(PluginRequestInternal pluginRequest) {
                return pluginRequest.getId();
            }
        });

        // Check for duplicates
        for (PluginId key : groupedById.keySet()) {
            Collection<PluginRequestInternal> pluginRequestsForId = groupedById.get(key);
            if (pluginRequestsForId.size() > 1) {
                PluginRequestInternal first = pluginRequests.get(0);
                throw new InvalidPluginRequestException(first, "Plugin with id '" + key + "' was already requested.");
            }
        }
        return pluginRequests;
    }
}
