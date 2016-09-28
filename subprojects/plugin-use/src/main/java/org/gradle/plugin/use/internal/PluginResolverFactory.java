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

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.internal.Factory;
import org.gradle.plugin.repository.internal.PluginRepositoryRegistry;
import org.gradle.plugin.repository.PluginRepository;
import org.gradle.plugin.repository.internal.PluginRepositoryInternal;
import org.gradle.plugin.use.resolve.internal.CompositePluginResolver;
import org.gradle.plugin.use.resolve.internal.CorePluginResolver;
import org.gradle.plugin.use.resolve.internal.NoopPluginResolver;
import org.gradle.plugin.use.resolve.internal.PluginResolver;
import org.gradle.plugin.use.resolve.service.internal.InjectedClasspathPluginResolver;
import org.gradle.plugin.use.resolve.service.internal.PluginResolutionServiceResolver;

import java.util.LinkedList;
import java.util.List;

class PluginResolverFactory implements Factory<PluginResolver> {

    private final PluginRegistry pluginRegistry;
    private final DocumentationRegistry documentationRegistry;
    private final PluginResolutionServiceResolver pluginResolutionServiceResolver;
    private final PluginRepositoryRegistry pluginRepositoryRegistry;
    private final InjectedClasspathPluginResolver injectedClasspathPluginResolver;

    PluginResolverFactory(
        PluginRegistry pluginRegistry,
        DocumentationRegistry documentationRegistry,
        PluginResolutionServiceResolver pluginResolutionServiceResolver,
        PluginRepositoryRegistry pluginRepositoryRegistry,
        InjectedClasspathPluginResolver injectedClasspathPluginResolver
    ) {
        this.pluginRegistry = pluginRegistry;
        this.documentationRegistry = documentationRegistry;
        this.pluginResolutionServiceResolver = pluginResolutionServiceResolver;
        this.pluginRepositoryRegistry = pluginRepositoryRegistry;
        this.injectedClasspathPluginResolver = injectedClasspathPluginResolver;
    }

    @Override
    public PluginResolver create() {
        return new CompositePluginResolver(createDefaultResolvers());
    }

    private List<PluginResolver> createDefaultResolvers() {
        List<PluginResolver> resolvers = new LinkedList<PluginResolver>();
        addDefaultResolvers(resolvers);
        return resolvers;
    }

    /**
     * Returns the default PluginResolvers used by Gradle.
     * <p>
     * The plugins will be searched in a chain from the first to the last until a plugin is found.
     * So, order matters.
     * <p>
     * <ol>
     *     <li>{@link NoopPluginResolver} - Only used in tests.</li>
     *     <li>{@link CorePluginResolver} - distributed with Gradle</li>
     *     <li>{@link InjectedClasspathPluginResolver} - from a TestKit test's ClassPath</li>
     *     <li>Resolvers based on the entries of the `pluginRepositories` block</li>
     *     <li>{@link PluginResolutionServiceResolver} - from Gradle Plugin Portal if no `pluginRepositories` were defined</li>
     * </ol>
     * <p>
     * This order is optimized for both performance and to allow resolvers earlier in the order
     * to mask plugins which would have been found later in the order.
     */
    private void addDefaultResolvers(List<PluginResolver> resolvers) {
        resolvers.add(new NoopPluginResolver(pluginRegistry));
        resolvers.add(new CorePluginResolver(documentationRegistry, pluginRegistry));

        if (!injectedClasspathPluginResolver.isClasspathEmpty()) {
            resolvers.add(injectedClasspathPluginResolver);
        }

        ImmutableList<PluginRepository> pluginRepositories = getPluginRepositories();
        if (pluginRepositories.isEmpty()) {
            resolvers.add(pluginResolutionServiceResolver);
        } else {
            addPluginRepositoryResolvers(resolvers, pluginRepositories);
        }
    }

    private ImmutableList<PluginRepository> getPluginRepositories() {
        pluginRepositoryRegistry.lock();
        return pluginRepositoryRegistry.getPluginRepositories();
    }

    private void addPluginRepositoryResolvers(List<PluginResolver> resolvers, ImmutableList<PluginRepository> pluginRepositories) {
        for (PluginRepository pluginRepository : pluginRepositories) {
            resolvers.add(asResolver(pluginRepository));
        }
    }

    private PluginResolver asResolver(PluginRepository pluginRepository) {
        return ((PluginRepositoryInternal) pluginRepository).asResolver();
    }
}
