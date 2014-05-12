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

package org.gradle.plugin.resolve.internal;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.plugins.ClassloaderBackedPluginDescriptorLocator;
import org.gradle.api.internal.plugins.PluginDescriptorLocator;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.plugin.resolve.portal.internal.PluginPortalResolver;

import java.util.LinkedList;
import java.util.List;

public class PluginResolverFactory {

    private final PluginRegistry pluginRegistry;
    private final DocumentationRegistry documentationRegistry;
    private final PluginPortalResolver pluginPortalResolver;

    public PluginResolverFactory(
            PluginRegistry pluginRegistry,
            DocumentationRegistry documentationRegistry,
            PluginPortalResolver pluginPortalResolver
    ) {
        this.pluginRegistry = pluginRegistry;
        this.documentationRegistry = documentationRegistry;
        this.pluginPortalResolver = pluginPortalResolver;
    }

    public PluginResolver createPluginResolver(ClassLoader scriptClassLoader) {
        List<PluginResolver> resolvers = new LinkedList<PluginResolver>();
        addDefaultResolvers(resolvers);
        CompositePluginResolver compositePluginResolver = new CompositePluginResolver(resolvers);

        PluginDescriptorLocator scriptClasspathPluginDescriptorLocator = new ClassloaderBackedPluginDescriptorLocator(scriptClassLoader);

        return new NotInPluginRegistryPluginResolverCheck(compositePluginResolver, pluginRegistry, scriptClasspathPluginDescriptorLocator);
    }

    private void addDefaultResolvers(List<PluginResolver> resolvers) {
        resolvers.add(new NoopPluginResolver());
        resolvers.add(new CorePluginResolver(documentationRegistry, pluginRegistry));
        resolvers.add(pluginPortalResolver);
    }

}
