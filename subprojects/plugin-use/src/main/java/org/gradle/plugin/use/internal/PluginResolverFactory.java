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

package org.gradle.plugin.use.internal;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.internal.Factory;
import org.gradle.plugin.use.resolve.internal.CompositePluginResolver;
import org.gradle.plugin.use.resolve.internal.CorePluginResolver;
import org.gradle.plugin.use.resolve.internal.NoopPluginResolver;
import org.gradle.plugin.use.resolve.internal.PluginResolver;
import org.gradle.plugin.use.resolve.service.internal.PluginResolutionServiceResolver;

import java.util.LinkedList;
import java.util.List;

public class PluginResolverFactory implements Factory<PluginResolver> {

    private final PluginRegistry pluginRegistry;
    private final DocumentationRegistry documentationRegistry;
    private final PluginResolutionServiceResolver pluginResolutionServiceResolver;

    public PluginResolverFactory(
            PluginRegistry pluginRegistry,
            DocumentationRegistry documentationRegistry,
            PluginResolutionServiceResolver pluginResolutionServiceResolver
    ) {
        this.pluginRegistry = pluginRegistry;
        this.documentationRegistry = documentationRegistry;
        this.pluginResolutionServiceResolver = pluginResolutionServiceResolver;
    }

    public PluginResolver create() {
        List<PluginResolver> resolvers = new LinkedList<PluginResolver>();
        addDefaultResolvers(resolvers);
        return new CompositePluginResolver(resolvers);
    }

    private void addDefaultResolvers(List<PluginResolver> resolvers) {
        resolvers.add(new NoopPluginResolver());
        resolvers.add(new CorePluginResolver(documentationRegistry, pluginRegistry));
        resolvers.add(pluginResolutionServiceResolver);
    }

}
